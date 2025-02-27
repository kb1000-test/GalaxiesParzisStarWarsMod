import com.github.hal4j.uritemplate.URITemplate
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType

plugins {
	id("com.modrinth.minotaur") version "2.+"
	id("io.github.CDAGaming.cursegradle") version "1.6.0"
}

buildscript {
	dependencies {
		classpath("org.apache.httpcomponents.client5:httpclient5-fluent:5.2.1")
		classpath("com.github.hal4j:uritemplate:1.3.0")
	}
}

modrinth {
	token.set(rootProject.findProperty("githubToken") as? String?)
	changelog.set(rootProject.file("CHANGELOG.md").readText())
	projectId.set("9rBI0wQz")
	versionType.set("alpha")
	uploadFile.set(tasks.remapJar as Any)
	required.project("P7dR8mSH") // Fabric API
	optional.project("mOgUt4GM") // Mod Menu
	optional.project("nfn13YXA") // REI
}

curseforge {
	apiKey = rootProject.findProperty("curseforgeToken") ?: ""
	project {
		id = "496522"
		changelogType = "markdown"
		changelog = rootProject.file("CHANGELOG.md")
		releaseType = "beta"
		relations {
			requiredDependency("fabric-api")
			optionalDependency("modmenu")
			optionalDependency("pswg-addon-clonewars")
			optionalDependency("roughly-enough-items")
		}
	}
}

tasks.curseforge {
	group = "publishing"
}

val github by tasks.register("github") {
	inputs.files(tasks.remapJar)

	group = "publishing"

	doFirst {
		val version = version as String
		val gson = Gson()
		val token = rootProject.findProperty("githubToken") as String
		val file = tasks.remapJar.get().archiveFile.get().asFile
		val createResponse = JsonParser.parseString(
			Request.post("https://api.github.com/repos/Parzivail-Modding-Team/GalaxiesParzisStarWarsMod/releases")
				.addHeader("Authorization", "Bearer $token")
				.bodyString(gson.toJson(jsonObject {
					"tag_name"(version)
					"target_commitish"(version)
					"name"(version)
					"body"(rootProject.file("CHANGELOG.md").readText())
					"draft"(true)
					"prerelease"(true)
				}), ContentType.APPLICATION_JSON)
				.execute()
				.returnContent()
				.asString(Charsets.UTF_8)
		).asJsonObject
		val uploadUrl = createResponse["upload_url"].asString
		Request.post(URITemplate(uploadUrl).expandOnly(mapOf("name" to file.name)).toURI())
			.addHeader("Authorization", "Bearer $token")
			.bodyFile(file, ContentType.create("application/java-archive"))
			.execute()
			.returnContent()
		val releaseUrl = createResponse["url"].asString
		Request.patch(releaseUrl)
			.addHeader("Authorization", "Bearer $token")
			.bodyString(gson.toJson(jsonObject {
				"draft"(false)
				"make_latest"(true)
			}), ContentType.APPLICATION_JSON)
			.execute()
			.returnContent()
	}
}

class JsonObjectDsl {
	val jsonObject = JsonObject()

	operator fun String.invoke(b: Boolean) = jsonObject.addProperty(this, b)
	operator fun String.invoke(s: String) = jsonObject.addProperty(this, s)
}

fun jsonObject(f: JsonObjectDsl.() -> Unit) = JsonObjectDsl().apply(f).jsonObject

tasks.register("modPublish") {
	dependsOn(tasks.modrinth, tasks.curseforge, github)
	group = "publishing"
}
