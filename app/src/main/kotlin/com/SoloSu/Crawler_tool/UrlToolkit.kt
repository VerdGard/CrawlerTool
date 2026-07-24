package com.SoloSu.Crawler_tool

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * URL 工具箱 (P2.12)
 * 编码/解码、参数提取、相对→绝对路径转换
 */
object UrlToolkit {

    fun encode(input: String): String {
        return try {
            URLEncoder.encode(input, "UTF-8")
        } catch (e: Exception) {
            input
        }
    }

    fun decode(input: String): String {
        return try {
            URLDecoder.decode(input, "UTF-8")
        } catch (e: Exception) {
            input
        }
    }

    data class UrlParam(
        val key: String,
        val value: String
    )

    fun extractParams(url: String): List<UrlParam> {
        val params = mutableListOf<UrlParam>()
        try {
            val queryStart = url.indexOf('?')
            if (queryStart == -1) return params
            val query = url.substring(queryStart + 1)
            val pairs = query.split("&")
            for (pair in pairs) {
                val eq = pair.indexOf('=')
                if (eq != -1) {
                    params.add(UrlParam(
                        key = decode(pair.substring(0, eq)),
                        value = decode(pair.substring(eq + 1))
                    ))
                } else if (pair.isNotBlank()) {
                    params.add(UrlParam(key = decode(pair), value = ""))
                }
            }
        } catch (_: Exception) {}
        return params
    }

    fun toAbsolute(baseUrl: String, relativePath: String): String {
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath
        }
        if (relativePath.startsWith("//")) {
            val protocol = if (baseUrl.startsWith("https")) "https:" else "http:"
            return "$protocol$relativePath"
        }
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, relativePath).toString()
        } catch (e: Exception) {
            "$baseUrl$relativePath"
        }
    }

    fun replaceTemplate(url: String, variables: Map<String, String>): String {
        var result = url
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", encode(value))
        }
        return result
    }

    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun extractDomain(url: String): String {
        return try {
            java.net.URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }
}
