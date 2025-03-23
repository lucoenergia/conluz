#!/bin/bash

# Configuration
CONTAINER_NAME="conluz"
IMAGE_BASE_NAME="conluz"
DOCKER_COMPOSE_PATH="docker-compose.yaml"

# Move to the deploy directory
cd "$(dirname "$0")" || exit

# Stop and remove only the running 'conluz' container if it exists
echo "Stopping and removing the running container..."
docker compose -f $DOCKER_COMPOSE_PATH stop $CONTAINER_NAME
docker compose -f $DOCKER_COMPOSE_PATH rm -f $CONTAINER_NAME

# Remove previous images
echo "Removing previous images..."
PREVIOUS_IMAGES=$(docker images "$IMAGE_BASE_NAME:*" -q)
for IMAGE in $PREVIOUS_IMAGES; do
  echo "Removing image $IMAGE..."
  docker rmi -f "$IMAGE"
done

# Update the code
echo "Updating code from Git..."
cd .. && git pull

# Build the JAR
echo "Building the project..."
./gradlew clean build -x test

# Move back to deploy directory
cd deploy || exit

VERSION=$(git describe --tags --abbrev=0)-$(git rev-parse --short HEAD)
IMAGE_NAME_VERSION="$IMAGE_BASE_NAME:$VERSION"
IMAGE_NAME_LATEST="$IMAGE_BASE_NAME:latest"

# Build the image with two tags
echo "Building the Docker image with tags $IMAGE_NAME_VERSION and $IMAGE_NAME_LATEST..."
docker build -t "$IMAGE_NAME_VERSION" -t $IMAGE_NAME_LATEST ..

# Run the container using Docker Compose
echo "Starting the container..."
docker compose -f $DOCKER_COMPOSE_PATH up -d conluz
