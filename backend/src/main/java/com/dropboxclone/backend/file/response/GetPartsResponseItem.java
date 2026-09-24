package com.dropboxclone.backend.file.response;

public record GetPartsResponseItem(Integer partNumber, String status, String fingerprint) {
}
