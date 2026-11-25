package com.hisab.server.database

import com.hisab.server.plugins.RequestFailure
import io.ktor.http.HttpStatusCode
import com.mongodb.client.model.Filters
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.Decimal128
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

@Serializable
data class PairBalance(val firstId: String, val secondId: String, val amount: String)

internal fun operation(name: String, value: Any) = Document("$" + name, value)
internal fun expression(name: String, vararg values: Any) = operation(name, values.toList())

internal fun ledgerAmountGroup(identity: Document, positive: Document): Document {
    val decimal = operation("toDecimal", operation("toString", "$" + "amount"))
    val signed = expression("cond", positive, decimal, expression("multiply", decimal, -1))
    val precise = expression("eq", decimal,
        expression("divide", operation("trunc", expression("multiply", decimal, 1000)), 1000))
    return operation("group", Document("_id", identity)
        .append("exact", operation("sum", expression("cond", precise, signed, 0)))
        .append("legacy", operation("sum", expression("cond", precise, 0, signed))))
}

internal fun ledgerNetAmount(row: Document): BigDecimal {
    fun amount(field: String): BigDecimal = when (val value = row[field]) {
        is Decimal128 -> if (value.isFinite) value.bigDecimalValue() else
            throw RequestFailure(HttpStatusCode.Conflict, "The ledger contains an invalid amount")
        is Number -> value.toString().toBigDecimal()
        else -> throw RequestFailure(HttpStatusCode.Conflict, "The ledger contains an invalid amount")
    }
    return (amount("exact") + amount("legacy").setScale(3, RoundingMode.HALF_UP)).setScale(3)
}

/** Reduce history in MongoDB; preserve the existing exact/legacy-subtotal rounding policy. */
internal suspend fun groupPairBalances(groupId: String, session: com.mongodb.reactivestreams.client.ClientSession? = null): List<PairBalance> {
    val first = expression("lte", "$" + "debtorId", "$" + "creditorId")
    val identity = Document("first", expression("cond", first, "$" + "debtorId", "$" + "creditorId"))
        .append("second", expression("cond", first, "$" + "creditorId", "$" + "debtorId"))
    val pipeline = listOf(operation("match", Filters.and(Filters.eq("groupId", groupId),
        Filters.expr(expression("ne", "$" + "debtorId", "$" + "creditorId")))), ledgerAmountGroup(identity, first))
    val query = if (session == null) MongoClientProvider.entryDirectory.aggregate<Document>(pipeline)
        else MongoClientProvider.entryDirectory.aggregate<Document>(session, pipeline)
    val rows = query.allowDiskUse(true).maxTime(10, TimeUnit.SECONDS).toList()
    return rows.map { row ->
        val id = row.get("_id", Document::class.java)
        PairBalance(id.getString("first"), id.getString("second"), ledgerNetAmount(row).toPlainString())
    }
}
