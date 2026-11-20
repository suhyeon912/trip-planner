package com.tripplanner.share

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 복구 코드 "ABCD-EFGH-JKLM" (헷갈리는 글자 0 · O · 1 · I 빼고 12자, 약 60비트).
 * Firebase 이메일 · 비밀번호 로그인으로 씀: 이메일 = 코드에서 만든 가짜 주소(메일은 보내지 않음), 비밀번호 = 코드.
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun newCode(): String = (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** 입력한 글자 → 12자 코드 (대소문자 · 줄표 · 띄어쓰기 무시, 틀린 모양이면 null) */
    fun normalize(raw: String): String? {
        val s = raw.uppercase().filter { it.isLetterOrDigit() }
        return s.takeIf { it.length == 12 && it.all { c -> c in ALPHABET } }
    }

    /** 보여 줄 때: "ABCD-EFGH-JKLM" */
    fun display(code: String) = code.chunked(4).joinToString("-")

    fun email(code: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("tripplanner:" + code).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        return "r$h@restore.tripplanner.app"
    }

    fun password(code: String) = "TP-$code"
}
