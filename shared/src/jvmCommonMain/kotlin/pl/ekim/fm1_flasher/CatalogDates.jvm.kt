package pl.ekim.fm1_flasher

internal actual fun catalogDateMillis(value: String): Long {
        if (value.isBlank()) return 0
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0)
    }
