package pl.ekim.fm1_flasher

import org.json.JSONObject

internal fun oauthToJson(c: OAuthConfiguration) = JSONObject().put("grant", c.grant.name).put("clientId", c.clientId)
    .put("authorizationUrl", c.authorizationUrl).put("tokenUrl", c.tokenUrl).put("scope", c.scope).put("clientAuth", c.clientAuth.name)
internal fun oauthFromJson(j: JSONObject?) = if (j == null) OAuthConfiguration() else OAuthConfiguration(
    OAuthGrant.valueOf(j.getString("grant")), j.getString("clientId"), j.getString("authorizationUrl"),
    j.getString("tokenUrl"), j.getString("scope"), OAuthClientAuth.valueOf(j.optString("clientAuth", "BASIC")))
