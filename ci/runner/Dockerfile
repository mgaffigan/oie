FROM docker:27-cli

RUN apk add --no-cache bash curl python3 py3-lxml py3-yaml

WORKDIR /app
COPY *.py /app/

ENTRYPOINT ["python3", "/app/run.py"]
