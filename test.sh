#!/bin/bash

# * Copyright (c) 2009-2018. Authors: see NOTICE file.
# *
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# *
# *      http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.


# Use this script to test locally the algorithm implementation, with default values for not Cytomine-related parameters.

CYTOMINE_HOST=$1
CYTOMINE_PUBLIC_KEY=$2
CYTOMINE_PRIVATE_KEY=$3
CYTOMINE_ID_PROJECT=$4
CYTOMINE_ID_SOFTWARE=$5
CYTOMINE_ID_TERMS=$6
CYTOMINE_ID_IMAGES=$7

IMAGE="cytomineuliege/s_stats-termarea"

ADD_HOST=""
if [[ $CYTOMINE_HOST = *"localhost"* ]];
then
    ADD_HOST="--add-host=${CYTOMINE_HOST}:172.17.0.1"
fi

docker build -t $IMAGE .
docker run $ADD_HOST -it $IMAGE $CYTOMINE_HOST $CYTOMINE_PUBLIC_KEY $CYTOMINE_PRIVATE_KEY $CYTOMINE_ID_PROJECT $CYTOMINE_ID_SOFTWARE $CYTOMINE_ID_TERMS $CYTOMINE_ID_IMAGES