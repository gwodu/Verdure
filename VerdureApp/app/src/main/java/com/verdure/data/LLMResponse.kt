package com.verdure.data

/**
 * Parsed LLM response with optional thinking section
 */
data class LLMResponse(
    val thinking: String?,
    val response: String
) {
    companion object {
        /**
         * Parse LLM output that may contain thinking and response sections
         * 
         * Expected format (but flexible):
         * <thinking>...</thinking>
         * <response>...</response>
         * 
         * Or just plain text if no sections found
         */
        fun parse(rawOutput: String): LLMResponse {
            // Try to extract thinking section
            val thinkingMatch = Regex("<thinking>(.*?)</thinking>", RegexOption.DOT_MATCHES_ALL)
                .find(rawOutput)
            val thinking = thinkingMatch?.groupValues?.get(1)?.trim()
            
            // Try to extract response section
            val responseMatch = Regex("<response>(.*?)</response>", RegexOption.DOT_MATCHES_ALL)
                .find(rawOutput)
            val response = responseMatch?.groupValues?.get(1)?.trim()
            
            return if (response != null) {
                // Found structured response
                LLMResponse(thinking, response)
            } else if (thinking != null) {
                // Found only thinking, treat rest as response
                val remainingText = rawOutput
                    .replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
                LLMResponse(thinking, remainingText.ifEmpty { rawOutput })
            } else {
                // No structure found, treat entire output as response
                LLMResponse(null, rawOutput.trim())
            }
        }
    }
}
