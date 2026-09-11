package com.example.commerce.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan("com.example.commerce")
@SpringBootApplication(
    scanBasePackages = ["com.example.commerce"],
    exclude = [DataSourceAutoConfiguration::class],
)
class CommerceApiApplication

fun main(args: Array<String>) {
    runApplication<CommerceApiApplication>(*args)
}
