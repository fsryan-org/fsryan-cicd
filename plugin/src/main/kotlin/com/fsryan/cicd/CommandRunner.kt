package com.fsryan.cicd

interface CommandRunner {
    fun runCommand(vararg arguments: String): String
}