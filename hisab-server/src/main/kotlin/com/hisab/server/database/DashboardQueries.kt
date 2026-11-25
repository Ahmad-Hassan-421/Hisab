package com.hisab.server.database

import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import org.bson.Document
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@Serializable
data class DashboardSummary(val totalDebt: String, val totalReceive: String, val groupCount: Long, val generatedAt: Long)

internal suspend fun dashboardSummary(actor: String): DashboardSummary = MongoClientProvider.transaction { session ->
    val positive = expression("eq", "$" + "debtorId", operation("literal", actor))
    val identity = Document("group", "$" + "groupId")
        .append("other", expression("cond", positive, "$" + "creditorId", "$" + "debtorId"))
    val groupAccess = Document("from", "hisabGroupDocument").append("let", Document("group", "$" + "_id.group"))
        .append("pipeline", listOf(
            operation("match", Filters.expr(expression("and",
                expression("eq", "$" + "_id", "$" + "$" + "group"),
                expression("in", operation("literal", actor), expression("ifNull", "$" + "members", emptyList<String>()))))),
            operation("project", Document("_id", 1))))
        .append("as", "access")
    val pipeline = listOf(
        operation("match", Filters.and(Filters.or(Filters.eq("debtorId", actor), Filters.eq("creditorId", actor)),
            Filters.expr(expression("ne", "$" + "debtorId", "$" + "creditorId")))),
        ledgerAmountGroup(identity, positive), operation("lookup", groupAccess),
        operation("match", Filters.exists("access.0")), operation("project", Document("exact", 1).append("legacy", 1)))
    var debt = BigDecimal.ZERO
    var receive = BigDecimal.ZERO
    // Stream reduced pairs; never download histories or accumulate all pairs in server memory.
    MongoClientProvider.entryDirectory.aggregate<Document>(session, pipeline).allowDiskUse(true)
        .maxTime(10, TimeUnit.SECONDS).batchSize(128).toFlow().collect { row ->
            val net = ledgerNetAmount(row)
            if (net.signum() > 0) debt += net else receive -= net
        }
    val groups = MongoClientProvider.groups.countDocuments(session, Filters.eq("members", actor))
    DashboardSummary(debt.setScale(3).toPlainString(), receive.setScale(3).toPlainString(), groups, System.currentTimeMillis())
}
