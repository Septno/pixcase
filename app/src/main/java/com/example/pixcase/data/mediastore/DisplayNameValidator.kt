package com.example.pixcase.data.mediastore

/**
 * DISPLAY_NAME 重命名的纯逻辑:文件名拆扩展名、拼回、校验非法输入。
 *
 * 对话框只让用户编辑「不含扩展名的主体名」,新名字由 [buildDisplayName] 自动拼回原扩展名
 * —— 这从根上消除了「改掉扩展名导致 MIME 关联失效」一类错误,校验因此只需覆盖主体名。
 * 纯 JVM 函数,见 DisplayNameValidatorTest。
 */
private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
private const val MAX_BASE_LENGTH = 100

/** 取「不含扩展名的主体名」:最后一个点之后是扩展名(非空),其余是主体;无点或点在首位则整体是主体。 */
internal fun baseNameOf(displayName: String): String {
    val dot = displayName.lastIndexOf('.')
    val hasExtension = dot > 0 && displayName.substring(dot + 1).isNotBlank()
    return if (hasExtension) displayName.substring(0, dot) else displayName
}

/** 主体名 + 原文件名的扩展名拼成新 DISPLAY_NAME;原名无扩展名则不加。 */
internal fun buildDisplayName(base: String, originalDisplayName: String): String {
    val trimmed = base.trim()
    val dot = originalDisplayName.lastIndexOf('.')
    val hasExtension = dot > 0 && originalDisplayName.substring(dot + 1).isNotBlank()
    return if (hasExtension) "$trimmed.${originalDisplayName.substring(dot + 1)}" else trimmed
}

/** 主体名是否合法:非空、无路径/通配类非法字符、长度有界。 */
internal fun isValidDisplayNameBase(base: String): Boolean {
    val trimmed = base.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_BASE_LENGTH) return false
    return !ILLEGAL_NAME_CHARS.containsMatchIn(trimmed)
}
