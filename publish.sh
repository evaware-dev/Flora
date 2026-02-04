#!/bin/bash

# Flora JitPack Release Script

set -e

echo "=== Flora JitPack Release ==="
echo ""

# Check if version is provided
if [ -z "$1" ]; then
    echo "Usage: ./publish.sh <version>"
    echo "Example: ./publish.sh 1.0.0"
    exit 1
fi

VERSION=$1

echo "Version: $VERSION"
echo ""

# Update version in build.gradle.kts
echo "Updating version in build.gradle.kts..."
sed -i "s/version = \".*\"/version = \"$VERSION\"/" build.gradle.kts

# Check if there are changes to commit
if [ -n "$(git status --porcelain)" ]; then
    echo "Committing version change..."
    git add build.gradle.kts
    git commit -m "Release version $VERSION"
fi

# Create git tag
echo "Creating git tag v$VERSION..."
git tag -a "v$VERSION" -m "Release version $VERSION"

# Push to remote
echo "Pushing to remote..."
BRANCH=$(git branch --show-current)
git push origin "$BRANCH"
git push origin "v$VERSION"

echo ""
echo "=== Release Complete ==="
echo "JitPack will now build the release."
echo "Check status at: https://jitpack.io/#evaware-dev/Flora/$VERSION"
echo ""
echo "To use in your project:"
echo "  implementation 'com.github.evaware-dev:Flora:$VERSION'"
