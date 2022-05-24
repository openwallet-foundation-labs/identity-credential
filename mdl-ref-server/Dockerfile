FROM python:3.8.13-alpine
ADD . /code
WORKDIR /code
RUN apk add --update \
  cargo \
  gcc \
  lcms2-dev \
  libffi-dev \
  musl-dev \
  openssl-dev \
  py-cffi \
  rust
RUN pip install -r requirements.txt
CMD ["python3", "mdl-ref-server.py", "--reset-with-testdata"]