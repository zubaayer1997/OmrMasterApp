package com.omrmaster.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val subject: String = "",
    val className: String = "",
    val questionCount: Int,
    val setCount: Int = 1, // 1, 2, or 4
    val negativeMarking: Boolean = false,
    val negativePenalty: Double = 0.25,
    val answerKeysJson: String = "{}", // {"ক":[0,1,2...], "খ":[...]}
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getSetCodes(): List<String> = when (setCount) {
        2 -> listOf("ক", "খ")
        4 -> listOf("ক", "খ", "গ", "ঘ")
        else -> listOf("ক")
    }

    fun getAnswerKeyMap(): Map<String, List<Int>> {
        val map = mutableMapOf<String, List<Int>>()
        val json = try {
            JSONObject(answerKeysJson)
        } catch (_: Exception) {
            JSONObject()
        }
        for (set in getSetCodes()) {
            val arr = json.optJSONArray(set)
            val list = MutableList(questionCount) { -1 }
            if (arr != null) {
                for (i in 0 until minOf(arr.length(), questionCount)) {
                    list[i] = arr.optInt(i, -1)
                }
            }
            map[set] = list
        }
        return map
    }

    fun withUpdatedAnswerKey(setCode: String, answers: List<Int>): ExamEntity {
        val json = try {
            JSONObject(answerKeysJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val orgArr = org.json.JSONArray()
        for (i in 0 until questionCount) {
            orgArr.put(answers.getOrElse(i) { -1 })
        }
        json.put(setCode, orgArr)
        return copy(answerKeysJson = json.toString())
    }

    fun isAnswerKeyConfigured(setCode: String): Boolean {
        val list = getAnswerKeyMap()[setCode] ?: return false
        return list.any { it in 0..3 }
    }
}
