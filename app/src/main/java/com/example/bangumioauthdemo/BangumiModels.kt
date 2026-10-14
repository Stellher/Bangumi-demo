package com.example.bangumioauthdemo

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class AnimeCard(
    val id: Int,
    val title: String,
    val original: String = "",
    val poster: String = "",
    val summary: String = "",
    val date: String = "",
    val score: Double = 0.0,
    val rank: Int = 0,
    val collectionCount: Int = 0,
    val category: Int = 2
)

data class BroadcastDay(val day: Int, val title: String, val entries: List<AnimeCard>)
data class PersonCard(val id: Int, val name: String, val image: String, val relation: String)
data class EpisodeCard(val id: Int, val number: String, val name: String, val airDate: String, val description: String)
data class CollectionCard(val subject: AnimeCard, val type: Int, val rate: Int, val comment: String, val tags: List<String>)
data class AccountCard(val id: Int, val username: String, val nickname: String, val avatar: String, val signature: String)

fun JSONObject.s(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun JSONArray.jsonItems(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.stringItems(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
fun JSONObject.pageData(): List<JSONObject> = optJSONArray("data")?.jsonItems()
    ?: optJSONArray("items")?.jsonItems() ?: emptyList()

fun JSONObject.card(): AnimeCard {
    val images = obj("images")
    val rating = obj("rating")
    return AnimeCard(
        id = optInt("id"),
        title = s("name_cn").ifBlank { s("name") }.ifBlank { "未命名条目" },
        original = s("name"),
        poster = images.s("large").ifBlank { images.s("common") }
            .ifBlank { images.s("medium") }.ifBlank { images.s("small") }
            .ifBlank { obj("images").s("grid") },
        summary = s("summary"),
        date = s("date").ifBlank { s("air_date") },
        score = rating.optDouble("score", 0.0),
        rank = optInt("rank"),
        collectionCount = obj("collection").optInt("collect"),
        category = optInt("type", 2)
    )
}

fun JSONObject.episode(): EpisodeCard = EpisodeCard(
    id = optInt("id"),
    number = s("sort").ifBlank { s("ep") }.ifBlank { s("disc") },
    name = s("name_cn").ifBlank { s("name") }.ifBlank { "未命名章节" },
    airDate = s("airdate"),
    description = s("desc")
)

fun JSONObject.person(relation: String): PersonCard {
    val character = optJSONObject("character")
        ?: optJSONObject("person")
        ?: this
    return PersonCard(
        id = character.optInt("id"),
        name = character.s("name"),
        image = character.obj("images").s("grid").ifBlank {
            character.obj("images").s("medium")
        }.ifBlank { character.obj("images").s("small") },
        relation = s("relation").ifBlank { s("type") }.ifBlank { relation }
    )
}

fun JSONObject.collectionCard(): CollectionCard {
    val subject = optJSONObject("subject") ?: JSONObject()
    return CollectionCard(
        subject = subject.card(),
        type = optInt("type", 1),
        rate = optInt("rate"),
        comment = s("comment"),
        tags = arr("tags").stringItems()
    )
}

fun JSONObject.account(): AccountCard {
    val avatars = obj("avatar")
    return AccountCard(
        id = optInt("id"),
        username = s("username"),
        nickname = s("nickname").ifBlank { s("username") },
        avatar = avatars.s("large").ifBlank { avatars.s("medium") },
        signature = s("sign")
    )
}

data class CollectionEdit(
    val type: Int,
    val rate: Int = 0,
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val isPrivate: Boolean = false
) {
    fun payload(): JSONObject {
        require(type in 1..5) { "无效的收藏状态" }
        require(rate in 0..10) { "评分必须在 0 至 10 之间" }
        require(tags.none { tag -> tag.any(Char::isWhitespace) }) {
            "Bangumi 收藏标签不能包含空格，请使用逗号分隔多个标签"
        }
        return JSONObject()
            .put("type", type)
            .put("rate", rate)
            .put("comment", comment)
            .put("tags", JSONArray(tags))
            .put("private", isPrivate)
    }
}

fun String.safeYear(): Int = toIntOrNull()?.coerceIn(1900, 2100) ?: LocalDate.now().year
