#!/usr/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters"
fi

FNAME="$1"

if [ -f "$FNAME" ]; then

    curl -D - -X POST \
    -F "package=@$FNAME" \
     http://localhost:8080/api/v2/packages

else

    echo "File $FNAME does not exist."

fi

