package com.piotrwalkusz.lebrb.lanlearn

import com.piotrwalkusz.lebrb.lanlearn.filereaders.FileDecoder
import com.piotrwalkusz.lebrb.lanlearn.filereaders.PdfFileDecoder
import com.piotrwalkusz.lebrb.lanlearn.filereaders.PlainTextFileDecoder
import com.piotrwalkusz.lebrb.lanlearn.lemmatizers.LanguageToolLemmatizer
import com.piotrwalkusz.lebrb.lanlearn.lemmatizers.Lemmatizer
import com.piotrwalkusz.lebrb.lanlearn.lemmatizers.StanfordLemmatizer
import com.piotrwalkusz.lebrb.lanlearn.wordsexporters.WordsExporter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.InputStream
import java.io.Reader


class LanLearnProcessor {

    private val fileReaders: Map<MediaType, FileDecoder> = mapOf(
            MediaType.PLAIN_TEXT to PlainTextFileDecoder(),
            MediaType.PDF to PdfFileDecoder())

    private val lemmatizers: Map<Language, Lemmatizer> = mapOf(
            Language.ENGLISH to StanfordLemmatizer(Language.ENGLISH),
            Language.GERMAN to LanguageToolLemmatizer(Language.GERMAN))


    fun translateAndExportWords(text: InputStream, mimeType: MediaType, dictionary: TranslationDictionary,
                                      exporter: WordsExporter): String {
        val words = countWords(text, mimeType, dictionary.sourceLanguage,  dictionary.getTranslatableWords()).keys
        val flashcards = words.associate { dictionary.getRepresentation(it)!! to dictionary.translate(it)!! }
        return exporter.export(flashcards)
    }

    fun countAndTranslateWords(text: InputStream, mimeType: MediaType, dictionary: TranslationDictionary)
            : Map<String, Pair<String, Int>> {

        val wordsFrequency = countWords(text, mimeType, dictionary.sourceLanguage, dictionary.getTranslatableWords())

        return wordsFrequency.map {
                    dictionary.getRepresentation(it.key)!! to Pair(dictionary.translate(it.key)!!, it.value)
                }.toMap()
    }
    fun translateWords(text: InputStream, mimeType: MediaType, dictionary: TranslationDictionary)
            : Map<String, String> {

        val wordsFrequency = countWords(text, mimeType, dictionary.sourceLanguage, dictionary.getTranslatableWords())

        return wordsFrequency.map {
                    dictionary.getRepresentation(it.key)!! to dictionary.translate(it.key)!!
                }.toMap()
    }

    fun countWords(source: InputStream, mimeType: MediaType, language: Language, dictionary: Set<String>)
            : Map<String, Int> {

        val lemmatizer = lemmatizers[language] ?: throw IllegalArgumentException("Language $language is not supported")
        val fileReader = fileReaders[mimeType] ?: throw IllegalArgumentException("Mime type $mimeType is not supported")
        val partsOfFile: Flux<Reader> = fileReader.read(source)

        return partsOfFile
                .flatMap { Mono.defer { Mono.just(lemmatizer.lemmatize(it, dictionary) ) }
                        .subscribeOn(Schedulers.parallel()) }
                .reduce { x, y -> mergeWordsFrequency(x, y) }
                .block()
    }

    private fun mergeWordsFrequency(x: Map<String, Int>, y: Map<String, Int>): Map<String, Int> {
        val result = x.toMutableMap()
        y.forEach { word, frequency -> result.merge(word, frequency, Int::plus) }
        return result
    }
}
