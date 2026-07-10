package com.two17industries.rideman.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromState(state: StravaUploadState): String = state.name

    @TypeConverter
    fun toState(value: String): StravaUploadState =
        runCatching { StravaUploadState.valueOf(value) }.getOrDefault(StravaUploadState.NONE)
}
