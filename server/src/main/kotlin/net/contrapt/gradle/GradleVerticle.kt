package net.contrapt.gradle

import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import net.contrapt.gradle.model.ProjectData
import net.contrapt.gradle.service.GradleService

class GradleVerticle : AbstractVerticle() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var gradleService : GradleService

    override fun start() {

        /**
         * Connect to the requested projectDir returning tasks and dependencies
         */
        vertx.eventBus().consumer<JsonObject>("gradle.connect") { message ->
            vertx.executeBlocking(Handler<Promise<JsonObject>> { future ->
                try {
                    val projectDir = message.body().getString("projectDir")
                    val extensionDir = message.body().getString("extensionDir")
                    gradleService = GradleService(projectDir, extensionDir)
                    val project = ProjectData(gradleService.getDependencySources(), gradleService.getClasspath(), "")
                    val projectJson = JsonObject.mapFrom(project)
                    vertx.eventBus().send("jvmcode.update-project", JsonObject().put("source", "Gradle").put("project", projectJson))
                    future.complete(projectJson)
                } catch (e: Exception) {
                    logger.error("Opening a project", e)
                    future.fail(e)
                }
            }, false, Handler { ar ->
                if (ar.failed()) {
                    message.fail(1, ar.cause().toString())
                } else {
                    message.reply(ar.result())
                }
            })
        }

        /**
         * Refresh gradle project (usually when one of the gradle related files change)
         */
        vertx.eventBus().consumer<JsonObject>("gradle.refresh") { message ->
            vertx.executeBlocking(Handler<Promise<JsonObject>> { future ->
                try {
                    gradleService.refresh()
                    val project = ProjectData(gradleService.getDependencySources(), gradleService.getClasspath(), "")
                    val projectJson = JsonObject.mapFrom(project)
                    vertx.eventBus().publish("jvmcode.update-project", JsonObject().put("source", "Gradle").put("project", projectJson))
                    future.complete(projectJson)
                } catch (e: Exception) {
                    logger.error("Opening a project", e)
                    future.fail(e)
                }
            }, false, Handler { ar ->
                if (ar.failed()) {
                    message.fail(1, ar.cause().toString())
                } else {
                    message.reply(ar.result())
                }
            })
        }

        /**
         * Get the list of available tasks
         */

        /**
         * Run the given task in this project
         */
        vertx.eventBus().consumer<JsonObject>("gradle.run-task") { message ->
            vertx.executeBlocking(Handler<Promise<JsonObject>> { future ->
                try {
                    val task = message.body().getString("task")
                    future.complete(JsonObject().put("taskOutput", gradleService.runTask(task)))
                } catch (e: Exception) {
                    logger.error("Running task", e)
                    future.fail(e)
                }
            }, false, Handler<AsyncResult<JsonObject>> { ar ->
                if (ar.failed()) {
                    message.fail(1, ar.cause().toString())
                } else message.reply(ar.result())
            })
        }

    }

}