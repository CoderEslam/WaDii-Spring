# Location APIs (Country / Province / City)

Base response envelope for every endpoint below (`com.doubleclick.wadii.utils.Response<T>`):

```json
{
  "data": {},
  "message": "string",
  "statusCode": 200,
  "timestamp": "2026-07-13T12:00:00"
}
```

`statusCode` comes from `ResponseType`: `SUCCESS=200`, `ERROR=400`, `UNAUTHORIZED=401`, `FORBIDDEN=403`, `NOT_FOUND=404`, `INTERNAL_SERVER_ERROR=500`.
The HTTP status of the response mirrors this code (200 / 400 / 401 / 403 / 404).

All three controllers extend a generic `Controller<Entity, DTO, ID>` base class (`ts/Controller.java`) which defines the common CRUD routes; each controller adds its own extra lookup endpoint.

> **Security note:** `SecurityConfig` permits `countries/**`, `provinces/**`, `cities/**` without a leading `/`. Spring's `AntPathRequestMatcher`/`MvcRequestMatcher` matches against the path starting with `/`, so as written these patterns will **not** match `/countries/...`, `/provinces/...`, `/cities/...`. In practice this means these endpoints currently fall through to `.anyRequest().authenticated()` and require a valid JWT, unless this is fixed to `/countries/**`, etc. Worth confirming against actual runtime behavior before relying on "public" access.

---

## CountryController

`@RequestMapping("/countries")` — `src/main/java/com/doubleclick/wadii/controller/CountryController.java`

| Method | Path | Auth required | Description |
|---|---|---|---|
| GET | `/countries/show/{id}` | (see note above) | Get a single country by id |
| POST | `/countries/insert` | JWT + role `ADMIN` | Create a country |
| POST | `/countries/update` | JWT + role `ADMIN` | Update a country |
| DELETE | `/countries/delete/{id}` | JWT + role `ADMIN` | Delete a country |
| GET | `/countries/show-all` | (see note above) | List all countries |

### CountryDto (request body for insert/update)
```json
{
  "id": 1,
  "name": "Morocco"
}
```
`isNotEmpty()` requires `name` to be non-null/non-blank.

### Country entity (response shape)
```json
{
  "id": 1,
  "name": "Morocco"
}
```

### Behavior notes
- `insert`: looks up the authenticated user by email (`Authentication.getName()`); if not found or not `ADMIN`, returns `SUCCESS` status but with `null` data and a rejection message (**note:** these authorization failures return HTTP 200, not 401/403, because they call `Response.response(null, msg, ResponseType.SUCCESS)`). Rejects duplicate country names (`ERROR`).
- `update`: same admin check; 404 if the country id doesn't exist.
- `delete`: same admin check; 404 if the country id doesn't exist.
- `show` / `show-all`: no admin/role check in the controller code itself.

---

## ProvinceController

`@RequestMapping("/provinces")` — `src/main/java/com/doubleclick/wadii/controller/ProvinceController.java`

| Method | Path | Auth required | Description |
|---|---|---|---|
| GET | `/provinces/show/{id}` | (see note above) | Get a single province by id |
| POST | `/provinces/insert` | (see note above) | Create a province |
| POST | `/provinces/update` | (see note above) | Update a province |
| DELETE | `/provinces/delete/{id}` | (see note above) | Delete a province |
| GET | `/provinces/show-all` | (see note above) | List all provinces |
| GET | `/provinces/by-country/{countryId}` | (see note above) | List provinces belonging to a country |

Unlike City/Country, **no admin-role check** is performed in `insert`/`update`/`delete` here — any authenticated (or, depending on the security-matcher issue above, possibly anonymous) caller can mutate provinces.

### ProvinceDto (request body for insert/update)
```json
{
  "id": 1,
  "name": "Casablanca-Settat",
  "countryId": 1
}
```
`isNotEmpty()` requires `name` non-blank and `countryId` non-null.

### Province entity (response shape)
```json
{
  "id": 1,
  "name": "Casablanca-Settat",
  "country": { "id": 1, "name": "Morocco" }
}
```

### Behavior notes
- `insert`: 404 if `countryId` doesn't exist; `ERROR` if a province with the same name already exists.
- `update`: 404 if the province `id` doesn't exist, then 404 if the new `countryId` doesn't exist.
- `delete`: 404 if the province id doesn't exist.
- `by-country/{countryId}`: uses `provinceRepository.findByCountryId_Id(countryId)`.

---

## CityController

`@RequestMapping("/cities")` — `src/main/java/com/doubleclick/wadii/controller/CityController.java`

| Method | Path | Auth required | Description |
|---|---|---|---|
| GET | `/cities/show/{id}` | (see note above) | Get a single city by id |
| POST | `/cities/insert` | JWT + role `ADMIN` | Create a city |
| POST | `/cities/update` | JWT + role `ADMIN` | Update a city |
| DELETE | `/cities/delete/{id}` | JWT + role `ADMIN` | Delete a city |
| GET | `/cities/show-all` | (see note above) | List all cities |
| GET | `/cities/by-province/{provinceId}` | (see note above) | List cities belonging to a province |

### CityDto (request body for insert/update)
```json
{
  "id": 1,
  "name": "Casablanca",
  "provinceId": 1
}
```
`isNotEmpty()` requires `name` non-blank and `provinceId` non-null.

### City entity (response shape)
```json
{
  "id": 1,
  "name": "Casablanca",
  "province": {
    "id": 1,
    "name": "Casablanca-Settat",
    "country": { "id": 1, "name": "Morocco" }
  }
}
```

### Behavior notes
- `insert`: same admin-role check pattern as Country; 404 if `provinceId` doesn't exist; `ERROR` if a city with the same name already exists.
- `update`: 404 if the city `id` doesn't exist, then 404 if the new `provinceId` doesn't exist.
- `delete`: 404 if the city id doesn't exist.
- `by-province/{provinceId}`: uses `cityRepository.findByProvinceId_Id(provinceId)`.

---

## Common admin-check pattern (Country & City, not Province)

```java
Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
if (userOptional.isEmpty()) {
    return Response.response(null, "User not exist", ResponseType.SUCCESS);
}
if (userOptional.get().getRole() != Role.ADMIN) {
    return Response.response(null, "You are not admin to do this action", ResponseType.SUCCESS);
}
```
Both failure branches return HTTP **200** with `statusCode: 200` in the body but `data: null` — clients must check `data`/`message`, not just the HTTP status, to detect these rejections.
