from locust import HttpUser, task, between


class RegistrationUser(HttpUser):
    wait_time = between(0.1, 0.3)

    @task
    def login_and_register(self):
        self.client.post("/login",json={"username": "test", "password": "test"})