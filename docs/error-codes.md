# Error codes

Every error from the API has the same JSON body:

```json
{
  "code": "BOX_NOT_FOUND",
  "message": "No box 12 for org 3f2a…",
  "details": [],
  "requestId": "85b13f81-bc66-4ac8-a1d2-91f0568b4061"
}
```

- `code` is fixed and never changes. Decide what to show the user from `code`, not from `message`.
- `message` is English text for developers. It may change, so don't match on it.
- `details` is a list of strings. It is empty unless the code below says otherwise.
- `requestId` is also written in the backend log. Show it to the user on a 500, so they can report it.
  The same value comes back in the `X-Request-Id` response header.

There is one exception to this body: a stale save of the experience (409, see "Experience") returns
the current experience document instead.

## Login and access

- `UNAUTHENTICATED` (401): no access token, or the token is invalid or expired. Send the user to log in.
- `FORBIDDEN` (403): the user is logged in but may not do this. For example, an action only an
  organisation manager may do, or deleting someone else's comment.
- `NOT_FOUND` (404): the organisation or project does not exist, or the user has no access to it. The
  two cases are not told apart on purpose. Also returned for a URL that has no endpoint.

## Request format

- `VALIDATION_ERROR` (400): a field is missing, blank or too long, or the experience document is invalid.
  `details` lists each problem as `"field: reason"`.
- `MALFORMED_REQUEST` (400): the body is not valid JSON, or a value has the wrong type.
- `INVALID_PARAMETER` (400): a value in the URL or query string has the wrong type, e.g. an unknown
  `?type=` or a non-UUID id.
- `MISSING_HEADER` (400): a required header is missing, e.g. `If-Match` when saving the experience.

## Experience

- `EXPERIENCE_NOT_READY` (422): the experience can't be published yet, or a published experience was
  saved with gaps. `details` lists the gaps, e.g. a start scene without a start card or a video block
  without a video.
- `START_CARD_IN_USE` (409): another experience on the same box already uses this start card.
- Stale save (409, no `code`): someone else saved the experience after you loaded it. The body is the
  current experience and the `ETag` header its version. Show it to the user and let them save again.

## Boxes

- `BOX_NOT_FOUND` (404): no such box in this organisation.
- `DUPLICATE_SERIAL_NUMBER` (409): another box already has this serial number.
- `PROJECT_NOT_IN_BOX_ORG` (422): the experience belongs to another organisation than the box.

## Scan objects

- `SCAN_OBJECT_NOT_FOUND` (404): no such scan object in this organisation.
- `DUPLICATE_RFID_TAG` (409): the RFID tag is already used, in this or any other organisation.
- `SCAN_OBJECT_TYPE_NOT_FOUND` (404): no such scan object type in this organisation.
- `SCAN_OBJECT_TYPE_IN_USE` (409): the type can't be deleted because a scan object, scene or rule still uses it.

## Media

- `MEDIA_NOT_FOUND` (404): no such media item in this organisation.
- `MEDIA_IN_USE` (409): the item can't be deleted because an experience still uses it.
- `INVALID_MEDIA_NAME` (400): the name is blank or longer than 255 characters.
- `INVALID_UPLOAD` (400): no file was uploaded, the file is empty, or the request is not a valid multipart upload.
- `MEDIA_TOO_LARGE` (413): the file is over the size limit for its kind (image, audio or video).
- `UNSUPPORTED_MEDIA_TYPE` (415): the file type is not allowed, or the file's content doesn't match it.

## Comments

- `COMMENT_NOT_FOUND` (404): no such comment on this experience.
- `INVALID_COMMENT` (400): a reply to a reply (replies are one level deep), resolving or reopening a reply
  instead of a thread start, or an unknown `status` filter (expected `open`, `resolved` or `all`).
- `COMMENT_HAS_REPLIES` (409): a thread start can't be deleted by its author once it has replies.

## Templates

- `TEMPLATE_NOT_FOUND` (404): no such template, or it belongs to another organisation.
- `INVALID_TEMPLATE` (400): unknown type, blank or too long name, description over 1000 characters, or
  `defaults` not a JSON object or over 64 KB.
- `TEMPLATE_NOT_EDITABLE` (403): global templates can't be changed or deleted.

## UNICHE Catalogue

Organisations, projects and permissions are checked with the UNICHE Catalogue. When it fails:

- `CONFLICT` (409): the Catalogue reported a conflict with the current state of a project or organisation.
- `UPSTREAM_VALIDATION_ERROR` (422): the Catalogue rejected the request as invalid.
- `UPSTREAM_TIMEOUT` (504): the Catalogue did not answer in time. Retrying later may work.
- `UPSTREAM_UNAVAILABLE` (503): the Catalogue can't be reached. Access can't be checked, so the request
  is refused.
- `UPSTREAM_INVALID_RESPONSE` (502): the Catalogue answered with something the backend can't use.

## Anything else

- `INTERNAL_ERROR` (500): an unexpected error in the backend. Show the `requestId` to the user.
