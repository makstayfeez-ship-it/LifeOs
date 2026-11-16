package com.lifeos.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Запуск маленькой ИИ-модели в телефоне. Внешний вид намеренно простой (строки на входе и выходе),
 * чтобы остальное приложение не зависело от типов библиотеки.
 */
class LlmRunner(modelPath: String, cacheDir: String) : AutoCloseable {
    private val engine = Engine(
        EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir),
    ).also { it.initialize() }

    /** Один ответ на сообщение. Может занять десятки секунд на слабом телефоне. */
    fun ask(system: String, prompt: String): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.6),
        )
        return engine.createConversation(config).use { it.sendMessage(prompt).toString() }
    }

    override fun close() = engine.close()
}
