package com.verdure.data

import kotlinx.serialization.Serializable

/**
 * Represents a goal/task the user wants to track
 * 
 * Example: "Graduate school applications"
 * Scope: "Track all emails and notifications about grad school applications, 
 *         deadlines, acceptances, rejections, recommendation letters"
 */
@Serializable
data class Incentive(
    val id: String,
    val name: String,
    val userDescription: String,
    val aiSummary: String,
    val keywords: List<String>,
    val createdAt: Long,
    val isActive: Boolean = true
)

/**
 * A notification matched to an incentive
 */
@Serializable
data class IncentiveMatch(
    val incentiveId: String,
    val notificationId: Int,
    val notificationAppName: String,
    val notificationTitle: String?,
    val notificationText: String?,
    val notificationTimestamp: Long,
    val actionSummary: String,
    val matchedAt: Long
)
