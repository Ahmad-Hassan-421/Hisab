package com.example.hisab.features.ledger.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hisab.R
import com.example.hisab.features.ledger.presentation.MemberBalanceUI
import com.example.hisab.util.Constants

@Composable
fun MemberBalanceCard(
    balance: MemberBalanceUI,
    onToggleExpand: () -> Unit,
    onShowDetails: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Center-left Circular Avatar with 1.dp outlineVariant border
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    AsyncImage(
                        model = Constants.profileImageUrl(balance.user.profilePicUrl),
                        contentDescription = "${balance.user.name}'s profile picture",
                        placeholder = painterResource(id = R.drawable.ic_default_google_avatar),
                        error = painterResource(id = R.drawable.ic_default_google_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Member Name and ID
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = balance.user.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (balance.user.uniqueId.isNotBlank()) "ID: ${balance.user.uniqueId}" else "Member",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Balance status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (balance.totalDebt.signum() > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Debt", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Text(
                                "PKR ${String.format("%.3f", balance.totalDebt)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (balance.totalReceive.signum() > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Will Receive", fontSize = 11.sp, color = Color(0xFF2E7D32))
                            Text(
                                "PKR ${String.format("%.3f", balance.totalReceive)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    if (balance.totalDebt.signum() == 0 && balance.totalReceive.signum() == 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "Settled",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = balance.isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (balance.debtBreakdown.isNotEmpty()) {
                        Text(
                            "Owes to:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        balance.debtBreakdown.forEach { debt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(debt.userName, fontSize = 13.sp)
                                Text(
                                    "PKR ${String.format("%.3f", debt.amount)}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (balance.receiveBreakdown.isNotEmpty()) {
                        Text(
                            "Owed by:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32)
                        )
                        balance.receiveBreakdown.forEach { receive ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(receive.userName, fontSize = 13.sp)
                                Text(
                                    "PKR ${String.format("%.3f", receive.amount)}",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { onShowDetails(balance.user.id, balance.user.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Detail about Debt")
                    }
                }
            }
        }
    }
}
