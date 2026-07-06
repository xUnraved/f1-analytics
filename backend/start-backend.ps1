$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.1"
Set-Location "C:\f1-analytics\backend"
& .\mvnw.cmd quarkus:dev *> backend-dev.log
