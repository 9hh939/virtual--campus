import requests

JAVA_BACKEND_URL = "http://127.0.0.1:12345"
BASE_URL = JAVA_BACKEND_URL


def get(path: str, params: dict, timeout: int = 5) -> requests.Response:
    url = f"{JAVA_BACKEND_URL}{path}"
    return requests.get(url, params=params, timeout=timeout)
