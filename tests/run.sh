#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
probe_test_dir=$(mktemp -d)
trap 'rm -rf "$probe_test_dir"' EXIT
javac -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/TransitionProbe.java app/src/main/java/in/scrollcount/probe/InstagramSignals.java tests/TransitionProbeTest.java tests/InstagramSignalsTest.java app/src/main/java/in/scrollcount/probe/YouTubeSignals.java tests/YouTubeSignalsTest.java app/src/main/java/in/scrollcount/probe/CountingEngine.java tests/CountingEngineTest.java tests/FastPositionTest.java app/src/main/java/in/scrollcount/probe/FastFingerprintTracker.java tests/FastFingerprintTest.java app/src/main/java/in/scrollcount/probe/FeedEvidence.java tests/FeedEvidenceTest.java tests/CheckpointTest.java app/src/main/java/in/scrollcount/probe/ReminderRule.java tests/ReminderRuleTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.TransitionProbeTest
java -cp "$probe_test_dir" in.scrollcount.probe.InstagramSignalsTest
java -cp "$probe_test_dir" in.scrollcount.probe.YouTubeSignalsTest
java -cp "$probe_test_dir" in.scrollcount.probe.CountingEngineTest
java -cp "$probe_test_dir" in.scrollcount.probe.FastPositionTest
java -cp "$probe_test_dir" in.scrollcount.probe.FastFingerprintTest
java -cp "$probe_test_dir" in.scrollcount.probe.FeedEvidenceTest
java -cp "$probe_test_dir" in.scrollcount.probe.CheckpointTest
java -cp "$probe_test_dir" in.scrollcount.probe.ReminderRuleTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/YouTubeLabels.java tests/YouTubeLabelsTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.YouTubeLabelsTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" tests/YouTubeRecoveryTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.YouTubeRecoveryTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/DailyBadge.java tests/DailyBadgeTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.DailyBadgeTest

javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/FriendSnapshot.java tests/FriendSnapshotTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.FriendSnapshotTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/HourlySyncPlan.java tests/HourlySyncPlanTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.HourlySyncPlanTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/FriendCipher.java tests/FriendCipherTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.FriendCipherTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/FriendInvitation.java tests/FriendInvitationTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.FriendInvitationTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/ShortVideoDetector.java app/src/main/java/in/scrollcount/probe/InstagramDetector.java app/src/main/java/in/scrollcount/probe/YouTubeDetector.java app/src/main/java/in/scrollcount/probe/SocialSignals.java app/src/main/java/in/scrollcount/probe/SocialDetector.java app/src/main/java/in/scrollcount/probe/Detectors.java tests/MultiAppTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.MultiAppTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/CallbackGuard.java tests/CallbackGuardTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.CallbackGuardTest
javac -cp "$probe_test_dir" -d "$probe_test_dir" app/src/main/java/in/scrollcount/probe/ReadLane.java tests/ReadLaneTest.java
java -cp "$probe_test_dir" in.scrollcount.probe.ReadLaneTest
