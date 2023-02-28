#!/bin/bash
git pull && ./gradlew build && screen -X -S fengsheng quit && screen -dmS fengsheng java -jar build/libs/fengsheng-1.0-SNAPSHOT.jar && screen -ls