package com.ragclient.android.core.designsystem

/** Decimal MB/kB, the same reading the documents grid footers use. */
fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000f) else "${bytes / 1_000} kB"