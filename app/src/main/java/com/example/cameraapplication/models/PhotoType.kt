package com.example.cameraapplication.models

import androidx.camera.core.AspectRatio

enum class PhotoType(val displayName: String, val aspectRatio: Float, val cameraXAspectRatio: Int, val needsCropping: Boolean) {
    ID_PHOTO("ID Photo (3:2)", 3f / 2f, AspectRatio.RATIO_4_3, true),
    MEMBER_PHOTO("Member Photo (4:3)", 4f / 3f, AspectRatio.RATIO_4_3, false),
    COMBO("Combo (16:9)", 16f / 9f, AspectRatio.RATIO_16_9, false);

    val aspectRatioText: String
        get() = when (this) {
            ID_PHOTO -> "3:2"
            MEMBER_PHOTO -> "4:3"
            COMBO -> "16:9"
        }
}
