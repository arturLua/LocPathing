package com.example.locpathing

interface GeocoderRepositoryContract {
    suspend fun getAddressFromLocation(lat: Double, lng: Double): String
}