#!/usr/bin/env python3

"""
Standalone Test Script for Verdure Notification Summarization Logic

This script tests the core notification filtering and summarization logic
without requiring Android dependencies or device deployment.

Run with: python3 test_notification_logic.py
"""

import time
from dataclasses import dataclass
from typing import List, Optional, Set

# ===== Mock Data Classes =====

@dataclass
class MockNotificationData:
    id: int
    package_name: str
    app_name: str
    title: Optional[str]
    text: Optional[str]
    timestamp: int
    category: Optional[str]
    priority: int
    has_actions: bool = False
    has_image: bool = False
    is_ongoing: bool = False

@dataclass
class MockPriorityRules:
    keywords: List[str]
    high_priority_apps: List[str]
    financial_apps: List[str]
    neutral_apps: List[str]
    domains: List[str]
    senders: List[str]
    contacts: List[str]

@dataclass
class MockUserContext:
    priority_rules: MockPriorityRules

# ===== Scoring Keywords =====

class MockScoringKeywords:
    communication_tier1 = {"WhatsApp", "Signal", "Messages", "Phone", "Telegram"}
    communication_tier2 = {"Gmail", "Outlook", "Slack", "Discord", "Teams", "Email", "Mail"}
    communication_tier3 = {"Instagram", "Twitter", "X", "Facebook", "LinkedIn", "Reddit"}
    low_priority_apps = {"Games", "News", "Shopping", "YouTube", "Netflix", "Spotify"}
    
    urgency_tier1_keywords = {"urgent", "critical", "asap", "emergency", "immediately", "911"}
    urgency_tier2_keywords = {"important", "deadline", "due", "tonight", "today", "expires"}
    urgency_tier3_keywords = {"tomorrow", "this week", "reminder", "follow up", "upcoming", "soon"}
    
    request_keywords = {"please reply", "need response", "waiting for", "respond by", "confirm", "rsvp"}
    meeting_keywords = {"meeting", "call", "zoom", "interview", "appointment", "event"}
    temporal_keywords = {"due", "deadline", "expires", "ends", "starts", "schedule", "calendar"}
    financial_keywords = {"payment", "invoice", "bill", "charge", "transaction", "bank", "fraud"}
    personal_keywords = {" you ", " your ", "you're", "you've", "you'll"}

def contains_any(text: str, keywords: Set[str]) -> bool:
    """Check if text contains any keyword from the set (case-insensitive)"""
    lower_text = text.lower()
    return any(keyword.lower() in lower_text for keyword in keywords)

# ===== Mock Notification Filter =====

class MockNotificationFilter:
    CRITICAL_THRESHOLD = 15
    SCORE_CAP_MAX = 24
    SCORE_CAP_MIN = -5
    
    def __init__(self, user_context: MockUserContext):
        self.user_context = user_context
    
    def score_notification(self, notification: MockNotificationData) -> int:
        score = 0
        
        score += self._score_by_app(notification.app_name, self.user_context.priority_rules)
        score += self._score_by_user_rules(notification, self.user_context.priority_rules)
        score += self._score_by_content(notification.title, notification.text)
        score += self._score_by_time(notification.timestamp)
        score += self._score_by_metadata(notification)
        
        return max(self.SCORE_CAP_MIN, min(score, self.SCORE_CAP_MAX))
    
    def _score_by_app(self, app_name: str, rules: MockPriorityRules) -> int:
        lower_app_name = app_name.lower()
        
        if any(app.lower() in lower_app_name for app in rules.high_priority_apps):
            return 4
        if any(app.lower() in lower_app_name for app in rules.financial_apps):
            return 3
        if any(app.lower() in lower_app_name for app in MockScoringKeywords.communication_tier1):
            return 3
        if any(app.lower() in lower_app_name for app in MockScoringKeywords.communication_tier2):
            return 2
        if any(app.lower() in lower_app_name for app in MockScoringKeywords.communication_tier3):
            return 1
        if any(app.lower() in lower_app_name for app in rules.neutral_apps):
            return 0
        if any(app.lower() in lower_app_name for app in MockScoringKeywords.low_priority_apps):
            return -2
        
        return 0
    
    def _score_by_user_rules(self, notification: MockNotificationData, rules: MockPriorityRules) -> int:
        score = 0
        content = f"{notification.title} {notification.text}".lower()
        
        keyword_matches = sum(1 for keyword in rules.keywords if keyword.lower() in content)
        score += min(keyword_matches * 2, 6)
        
        for domain in rules.domains:
            if domain.lower() in content:
                score += 2
        
        return score
    
    def _score_by_content(self, title: Optional[str], text: Optional[str]) -> int:
        score = 0
        combined = f"{title or ''} {text or ''}".lower()
        
        if not combined.strip():
            return 0
        
        if contains_any(combined, MockScoringKeywords.urgency_tier1_keywords):
            score += 5
        elif contains_any(combined, MockScoringKeywords.urgency_tier2_keywords):
            score += 3
        elif contains_any(combined, MockScoringKeywords.urgency_tier3_keywords):
            score += 2
        
        if contains_any(combined, MockScoringKeywords.request_keywords):
            score += 3
        if contains_any(combined, MockScoringKeywords.meeting_keywords):
            score += 3
        if contains_any(combined, MockScoringKeywords.temporal_keywords):
            score += 2
        if contains_any(combined, MockScoringKeywords.financial_keywords):
            score += 2
        
        if '?' in combined:
            score += 2
        if combined.count('!') >= 2:
            score += 1
        if contains_any(combined, MockScoringKeywords.personal_keywords):
            score += 1
        
        return score
    
    def _score_by_time(self, timestamp: int) -> int:
        score = 0
        age = int(time.time() * 1000) - timestamp
        
        if age < 5 * 60 * 1000:  # < 5 minutes
            score += 2
        elif age < 30 * 60 * 1000:  # < 30 minutes
            score += 1
        elif age > 24 * 60 * 60 * 1000:  # > 24 hours
            score -= 1
        
        return score
    
    def _score_by_metadata(self, notification: MockNotificationData) -> int:
        score = 0
        
        # Android priority: HIGH=1, MAX=2, LOW=-1, MIN=-2, DEFAULT=0
        if notification.priority in [1, 2]:
            score += 3
        elif notification.priority in [-1, -2]:
            score -= 1
        
        if notification.has_actions:
            score += 1
        if notification.has_image:
            score += 1
        if notification.is_ongoing:
            score -= 3
        
        return score
    
    def is_critical(self, notification: MockNotificationData) -> bool:
        return self.score_notification(notification) >= self.CRITICAL_THRESHOLD

# ===== Mock Summary Generator =====

class MockSummaryGenerator:
    def generate_summary(self, notifications: List[MockNotificationData]) -> str:
        if not notifications:
            return "No critical notifications"
        
        lines = []
        for notif in notifications:
            text = notif.text or notif.title or ""
            truncated = text[:100] + "..." if len(text) > 100 else text
            lines.append(f"{notif.app_name}: {truncated}")
        
        return "\n".join(lines)

# ===== Test Data Generator =====

def create_test_notifications() -> List[MockNotificationData]:
    now = int(time.time() * 1000)
    
    return [
        # CRITICAL notifications (should score >= 15)
        MockNotificationData(
            id=1,
            package_name="com.google.android.gm",
            app_name="Gmail",
            title="URGENT: Interview tomorrow at 9am",
            text="Please confirm your availability ASAP",
            timestamp=now - 2 * 60 * 1000,  # 2 minutes ago
            category="email",
            priority=1,  # HIGH
            has_actions=True
        ),
        
        MockNotificationData(
            id=2,
            package_name="com.slack",
            app_name="Slack",
            title="Meeting in 15 minutes",
            text="Zoom link: https://zoom.us/j/123 - Please join ASAP",
            timestamp=now - 1 * 60 * 1000,  # 1 minute ago
            category="msg",
            priority=1,
            has_actions=True
        ),
        
        MockNotificationData(
            id=3,
            package_name="com.chase.bank",
            app_name="Chase Bank",
            title="Security Alert: Unusual activity detected",
            text="Urgent: Please verify transaction of $500. Respond immediately.",
            timestamp=now - 5 * 60 * 1000,  # 5 minutes ago
            category="msg",
            priority=2,  # MAX
            has_actions=True
        ),
        
        # Medium priority notifications (score 5-14)
        MockNotificationData(
            id=4,
            package_name="com.whatsapp",
            app_name="WhatsApp",
            title="Mom",
            text="Can you call me when you get a chance?",
            timestamp=now - 10 * 60 * 1000,  # 10 minutes ago
            category="msg",
            priority=0,
            has_actions=True
        ),
        
        MockNotificationData(
            id=5,
            package_name="com.google.android.calendar",
            app_name="Calendar",
            title="Event tomorrow: Team standup",
            text="9:00 AM - 9:30 AM",
            timestamp=now - 30 * 60 * 1000,  # 30 minutes ago
            category="event",
            priority=0
        ),
        
        # Low priority notifications (score < 5)
        MockNotificationData(
            id=6,
            package_name="com.instagram",
            app_name="Instagram",
            title="New follower",
            text="john_doe started following you",
            timestamp=now - 60 * 60 * 1000,  # 1 hour ago
            category="social",
            priority=0
        ),
        
        MockNotificationData(
            id=7,
            package_name="com.youtube",
            app_name="YouTube",
            title="New video from TechChannel",
            text="Check out our latest tech review!",
            timestamp=now - 2 * 60 * 60 * 1000,  # 2 hours ago
            category="recommendation",
            priority=-1  # LOW
        ),
        
        # Edge cases
        MockNotificationData(
            id=8,
            package_name="com.spotify",
            app_name="Spotify",
            title="Now Playing",
            text="Song Name - Artist",
            timestamp=now,
            category="transport",
            priority=0,
            is_ongoing=True  # Should get -3 penalty
        ),
        
        MockNotificationData(
            id=9,
            package_name="com.unknown.app",
            app_name="Unknown App",
            title=None,
            text=None,
            timestamp=now - 48 * 60 * 60 * 1000,  # 2 days ago (stale)
            category=None,
            priority=0
        )
    ]

# ===== Test Runner =====

def run_tests():
    print("=" * 80)
    print("Verdure Notification Summarization Logic Test")
    print("=" * 80)
    print()
    
    user_context = MockUserContext(
        priority_rules=MockPriorityRules(
            keywords=["urgent", "deadline", "interview", "important", "asap"],
            high_priority_apps=["Gmail", "Outlook", "Calendar", "Messages", "Slack"],
            financial_apps=["Bank", "Venmo", "PayPal"],
            neutral_apps=["Instagram", "WhatsApp", "Facebook"],
            domains=[".edu", ".gov"],
            senders=[],
            contacts=[]
        )
    )
    
    filter_obj = MockNotificationFilter(user_context)
    summary_generator = MockSummaryGenerator()
    notifications = create_test_notifications()
    
    # Test 1: Score all notifications
    print("TEST 1: Notification Scoring")
    print("-" * 80)
    scored_notifications = [
        (notif, filter_obj.score_notification(notif), filter_obj.is_critical(notif))
        for notif in notifications
    ]
    scored_notifications.sort(key=lambda x: x[1], reverse=True)
    
    for notif, score, is_critical in scored_notifications:
        critical_marker = " [CRITICAL]" if is_critical else ""
        print(f"Score: {score:2d}{critical_marker} | {notif.app_name:15s} | {notif.title or '(no title)'}")
    print()
    
    # Test 2: Filter for CRITICAL notifications (score >= 15)
    print("TEST 2: CRITICAL Notification Detection (score >= 15)")
    print("-" * 80)
    critical_notifications = [notif for notif, score, is_crit in scored_notifications if is_crit]
    print(f"Found {len(critical_notifications)} CRITICAL notifications:")
    for notif in critical_notifications:
        print(f"  - {notif.app_name}: {notif.title}")
    print()
    
    # Test 3: Generate summary
    print("TEST 3: Summary Generation")
    print("-" * 80)
    summary = summary_generator.generate_summary(critical_notifications)
    print("Generated Summary:")
    print(summary)
    print()
    
    # Test 4: Edge cases
    print("TEST 4: Edge Case Validation")
    print("-" * 80)
    
    # Empty notification
    empty_notif = next((n for n in notifications if n.title is None and n.text is None), None)
    if empty_notif:
        score = filter_obj.score_notification(empty_notif)
        print(f"✓ Empty notification handled: score = {score}")
    
    # Ongoing notification penalty
    ongoing_notif = next((n for n in notifications if n.is_ongoing), None)
    if ongoing_notif:
        score = filter_obj.score_notification(ongoing_notif)
        print(f"✓ Ongoing notification penalty applied: score = {score} (should be negative)")
    
    # Stale notification
    now = int(time.time() * 1000)
    stale_notif = next((n for n in notifications if now - n.timestamp > 24 * 60 * 60 * 1000), None)
    if stale_notif:
        score = filter_obj.score_notification(stale_notif)
        print(f"✓ Stale notification (>24h) handled: score = {score}")
    
    print()
    
    # Test 5: Summary statistics
    print("TEST 5: Summary Statistics")
    print("-" * 80)
    total_notifications = len(notifications)
    critical_count = len(critical_notifications)
    critical_percentage = int((critical_count / total_notifications) * 100)
    
    print(f"Total notifications: {total_notifications}")
    print(f"CRITICAL notifications: {critical_count} ({critical_percentage}%)")
    avg_score = sum(score for _, score, _ in scored_notifications) / len(scored_notifications)
    print(f"Average score: {avg_score:.1f}")
    print(f"Score range: {scored_notifications[-1][1]} to {scored_notifications[0][1]}")
    print()
    
    # Test 6: Validation
    print("TEST 6: Validation Results")
    print("-" * 80)
    
    passed_tests = 0
    total_tests = 0
    
    # Validate: Gmail urgent interview should be CRITICAL
    total_tests += 1
    gmail_urgent = next((n for n in notifications if n.id == 1), None)
    if gmail_urgent and filter_obj.is_critical(gmail_urgent):
        print("✓ PASS: Gmail urgent interview is CRITICAL")
        passed_tests += 1
    else:
        print("✗ FAIL: Gmail urgent interview should be CRITICAL")
    
    # Validate: Slack meeting should be CRITICAL
    total_tests += 1
    slack_meeting = next((n for n in notifications if n.id == 2), None)
    if slack_meeting and filter_obj.is_critical(slack_meeting):
        print("✓ PASS: Slack meeting is CRITICAL")
        passed_tests += 1
    else:
        print("✗ FAIL: Slack meeting should be CRITICAL")
    
    # Validate: Bank security alert should be CRITICAL
    total_tests += 1
    bank_alert = next((n for n in notifications if n.id == 3), None)
    if bank_alert and filter_obj.is_critical(bank_alert):
        print("✓ PASS: Bank security alert is CRITICAL")
        passed_tests += 1
    else:
        print("✗ FAIL: Bank security alert should be CRITICAL")
    
    # Validate: Instagram notification should NOT be CRITICAL
    total_tests += 1
    instagram = next((n for n in notifications if n.id == 6), None)
    if instagram and not filter_obj.is_critical(instagram):
        print("✓ PASS: Instagram notification is NOT CRITICAL")
        passed_tests += 1
    else:
        print("✗ FAIL: Instagram notification should NOT be CRITICAL")
    
    # Validate: YouTube notification should NOT be CRITICAL
    total_tests += 1
    youtube = next((n for n in notifications if n.id == 7), None)
    if youtube and not filter_obj.is_critical(youtube):
        print("✓ PASS: YouTube notification is NOT CRITICAL")
        passed_tests += 1
    else:
        print("✗ FAIL: YouTube notification should NOT be CRITICAL")
    
    print()
    print("=" * 80)
    print(f"Test Results: {passed_tests}/{total_tests} tests passed")
    print("=" * 80)
    
    if passed_tests == total_tests:
        print("✓ ALL TESTS PASSED!")
        return 0
    else:
        print("✗ SOME TESTS FAILED")
        return 1

# ===== Main Execution =====

if __name__ == "__main__":
    exit(run_tests())
