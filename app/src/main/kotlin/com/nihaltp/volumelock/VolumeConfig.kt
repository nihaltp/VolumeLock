package com.nihaltp.volumelock

/**
 * Represents the media volume configuration for a foreground application.
 *
 * @property defaultVolume The fallback media volume when no background player is active.
 * @property pairings A map of background audio player packages to the desired media volume when active.
 */
data class VolumeConfig(
    val defaultVolume: Int,
    val pairings: Map<String, Int>
)
