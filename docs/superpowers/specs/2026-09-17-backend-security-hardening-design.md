# Backend Security Hardening — Image Upload Path

## Context

PhotoBooth is being extended into a full-stack traveler photo/album app with a
new frontend (React, with a globe or map visualization — a separate
sub-project not covered here). Because users will upload their own images,
the existing upload path needs to be hardened before frontend work builds on
top of it. This spec covers backend security only: malicious file content,
unauthorized access to other users' photos, upload abuse/resource
exhaustion, and (deferred) content moderation.

Today, `ImageService`/`ImageController` accept any multipart file, upload it
to Cloudflare R2 essentially as-is, and store/return a **public** R2 URL —
anyone with the link can view an image regardless of authentication. There is
no file-type verification beyond what the client claims, no size enforcement
beyond Spring defaults, no malware scanning, and no rate limiting.

## Goals

- Verify uploaded files are genuinely images, not spoofed/polyglot content.
- Scan uploads for malware before they are ever stored.
- Make stored images private: only the owning user can ever obtain a URL to
  view one.
- Rate-limit uploads per user to prevent abuse/resource exhaustion.
- Keep the pipeline synchronous and simple — no new async/status machinery.

## Non-goals

- Automated content moderation (CSAM/abuse detection APIs). Deferred until
  the app has a sharing/reporting feature; noted as a future follow-up.
- Admin access to other users' individual images. Admins keep their existing
  ability to disable/delete accounts, but do not gain image visibility.
- Frontend work of any kind.
- Rate limiting or hardening of endpoints outside the image upload path
  (e.g. login brute-force protection) — out of scope for this spec.

## Architecture Overview

The upload path (`POST /albums/{albumId}/images`) gains a validation
pipeline in front of the existing R2 upload call, and image reads switch
from public URLs to backend-issued presigned URLs. Two new services join
`docker-compose.yml`:

- **ClamAV** (`clamav/clamav` image) — malware scanning via clamd's
  `INSTREAM` protocol.
- **Redis** — shared counters for rate limiting (Bucket4j-backed).

## Upload Pipeline

Within the existing synchronous request/response cycle, in order:

1. **Rate limit check** — Bucket4j bucket keyed by user id, backed by Redis.
   Default: 20 uploads/user/hour. Exceeding it returns `429` with error code
   `RATE_LIMITED`.
2. **Size check** — enforced both at `spring.servlet.multipart.max-file-size`
   (server-level cutoff) and explicitly in the service (default 10MB), so the
   error is a clean `400 FILE_TOO_LARGE` rather than a container-level
   rejection.
3. **Content sniffing** — Apache Tika detects the actual media type from
   file bytes and rejects anything that isn't JPEG/PNG/WebP, regardless of
   the declared `Content-Type` or file extension. `400 INVALID_IMAGE_TYPE`
   on mismatch.
4. **Re-encode** — Java `ImageIO`/Thumbnailator decodes and re-encodes the
   image into a normalized format, stripping EXIF/metadata and capping
   dimensions at 4096px on the longest side. This also collapses most
   polyglot/embedded-payload tricks, since only genuine pixel data survives
   re-encoding.
5. **Malware scan** — the re-encoded bytes are streamed to ClamAV via
   `INSTREAM`. A positive match returns `422 INFECTED_FILE`; the file is
   never uploaded to R2.
6. **Upload to R2** — stored under a per-owner-prefixed key, e.g.
   `users/{userId}/albums/{albumId}/{imageId}`. The bucket's public-read ACL
   is removed; objects are private.

Each stage short-circuits on failure — nothing partial is ever written to
R2, and a failed scan/validation never reaches storage.

## Data Model Change

`Image.img` (currently a public URL string) is renamed/repurposed as
`Image.objectKey`, storing only the R2 object key.

Migration `V6__image_object_key.sql`:
- Adds `object_key` column to `image`.
- Since existing dev data predates ownership/private-storage semantics
  (consistent with how `V4` truncated pre-ownership data), existing rows are
  truncated rather than migrated — this is a dev database with no production
  data to preserve.
- Drops the old `img` column.

`GET` endpoints that return image data generate a presigned GET URL via
`S3Presigner` at response time (15-minute expiry), scoped to the requesting
owner's own images only. Presigned URLs are never persisted or returned to
any user other than the image's owner.

## Error Handling

Each validation stage maps to a distinct 4xx with a machine-readable error
code, consistent with the existing `HAS_ALBUMS`-style pattern in
`AdminController`:

| Stage | Status | Code |
|---|---|---|
| Rate limit exceeded | 429 | `RATE_LIMITED` |
| File too large | 400 | `FILE_TOO_LARGE` |
| Not a genuine image / type mismatch | 400 | `INVALID_IMAGE_TYPE` |
| Malware detected | 422 | `INFECTED_FILE` |

## Testing

- Unit tests per validator: bad magic bytes, oversized file, EXIF/metadata
  stripped after re-encode, dimension capping.
- Mockito mocks for the ClamAV client and the Bucket4j/Redis boundary,
  consistent with the existing service test style (`AlbumServiceTest`,
  `ImageServiceTest`).
- `@WebMvcTest` additions to `ImageControllerTest` covering the new
  error-code responses for each rejection path.
- Presigned URL generation tested by verifying `S3Presigner` is invoked with
  the correct key and that a non-owner request never reaches URL generation
  (existing ownership-check tests extend naturally here).

## Configuration Additions

New properties (env-overridable, following the existing `application.properties`
pattern):

- `upload.max-file-size-bytes` (default `10485760` / 10MB)
- `upload.max-dimension-px` (default `4096`)
- `upload.rate-limit.max-per-hour` (default `20`)
- `upload.presigned-url.expiry-minutes` (default `15`)
- `clamav.host`, `clamav.port`
- `redis.host`, `redis.port` (or `spring.data.redis.*` if using Spring Data
  Redis directly)

## Future Follow-ups (explicitly deferred)

- Content moderation (automated scanning for illegal/abusive content) once
  album sharing exists and there's a real exposure surface.
- A user-facing report mechanism, which would also justify revisiting admin
  image visibility.
- Rate limiting / brute-force protection on `/auth/**`.
