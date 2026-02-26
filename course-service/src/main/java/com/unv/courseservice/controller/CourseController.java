package com.unv.courseservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {

    @GetMapping("/courses")
    public String getCourses() throws InterruptedException {

        Thread.sleep(80);
        return "COURSE_LIST";
    }
}
