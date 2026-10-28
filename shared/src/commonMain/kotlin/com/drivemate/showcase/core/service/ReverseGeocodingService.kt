package com.drivemate.showcase.core.service

interface ReverseGeocodingService {
    suspend fun resolve(lat: Double, lng: Double): String?
}
