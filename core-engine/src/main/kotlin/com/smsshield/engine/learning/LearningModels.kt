package com.smsshield.engine.learning

enum class SampleLabel {
    SPAM,
    SAFE
}

data class TrainingSampleInput(
    val sender: String = "",
    val body: String,
    val label: SampleLabel
)

enum class FeatureType {
    UNIGRAM,
    BIGRAM,
    BEHAVIOR
}

data class LearnedFeature(
    val key: String,
    val type: FeatureType,
    val spamDocCount: Int,
    val safeDocCount: Int,
    val weight: Double
)

data class LearnedModelSnapshot(
    val totalSpamSamples: Int,
    val totalSafeSamples: Int,
    val features: Map<String, LearnedFeature>
)

data class DiscoveredPattern(
    val phrase: String,
    val type: FeatureType,
    val spamCount: Int,
    val safeCount: Int,
    val confidenceScore: Double
)

data class LearningReport(
    val totalSpamSamples: Int,
    val totalSafeSamples: Int,
    val topSpamPhrases: List<DiscoveredPattern>,
    val topSafePhrases: List<DiscoveredPattern>,
    val behavioralInsights: List<String>,
    val suggestedKeywords: List<String>
)

data class AdaptiveEvaluation(
    val scoreDelta: Int,
    val matchedPatterns: List<String>
)
