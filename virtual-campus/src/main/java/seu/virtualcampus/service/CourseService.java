package seu.virtualcampus.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seu.virtualcampus.domain.Course;
import seu.virtualcampus.domain.CourseSelection;
import seu.virtualcampus.domain.CourseStats;
import seu.virtualcampus.mapper.CourseMapper;
import seu.virtualcampus.mapper.CourseSelectionMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程服务类。
 * <p>
 * 提供课程的增删改查、选课、退课、冲突检测等相关业务逻辑。
 */
@Service
public class CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseSelectionMapper courseSelectionMapper;

    /**
     * 新增课程。
     *
     * @param course 课程对象。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseHallCache", allEntries = true)
    public void courseAdd(Course course) {
        course.setCoursePeopleNumber(0);
        courseMapper.courseAdd(course);
    }

    /**
     * 删除课程。
     *
     * @param courseId 课程ID。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseHallCache", allEntries = true)
    public void courseDelete(String courseId) {
        courseSelectionMapper.findSelectionsByCourseId(courseId)
                .forEach(selection -> dropCourse(selection.getStudentId(), courseId));
        courseMapper.courseDelete(courseId);
    }

    /**
     * 更新课程信息。
     *
     * @param course 课程对象。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseHallCache", allEntries = true)
    public void courseUpdate(Course course) {
        Course existingCourse = courseMapper.courseFind(course.getCourseId());
        if (existingCourse != null) {
            course.setCoursePeopleNumber(existingCourse.getCoursePeopleNumber());
        } else {
            course.setCoursePeopleNumber(0);
        }
        courseMapper.courseUpdate(course);
    }

    /**
     * 根据ID查询课程。
     *
     * @param courseId 课程ID。
     * @return 对应的课程对象，若不存在则返回null。
     */
    public Course courseFind(String courseId) {
        return courseMapper.courseFind(courseId);
    }

    /**
     * 获取所有课程。
     *
     * @return 所有课程列表。
     */
    @Cacheable(value = "courseHallCache", key = "'allCourses'")
    public List<Course> getAllCourses() {
        return courseMapper.findAllCourses();
    }

    /**
     * 获取课程统计信息。
     *
     * @param courseId 课程ID。
     * @return 课程统计信息对象。
     */
    public CourseStats getCourseStats(String courseId) {
        return courseMapper.getCourseStats(courseId);
    }

    /**
     * 学生选课。
     *
     * @param studentId 学生ID。
     * @param courseId  课程ID。
     * @return 选课成功返回true，否则返回false。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseHallCache", allEntries = true)
    public boolean selectCourse(String studentId, String courseId) {
        Course course = courseMapper.courseFind(courseId);
        if (course == null) {
            return false;
        }

        if (course.getCoursePeopleNumber() >= course.getCourseCapacity()) {
            return false;
        }

        if (courseSelectionMapper.checkSelection(studentId, courseId) > 0) {
            return false;
        }

        List<String> conflicts = checkCourseConflicts(studentId, courseId);
        if (!conflicts.isEmpty()) {
            return false;
        }

        CourseSelection selection = new CourseSelection();
        selection.setStudentId(studentId);
        selection.setCourseId(courseId);
        int result = courseSelectionMapper.selectCourse(selection);

        if (result > 0) {
            course.setCoursePeopleNumber(course.getCoursePeopleNumber() + 1);
            courseMapper.courseUpdate(course);
            return true;
        }

        return false;
    }

    /**
     * 学生退课。
     *
     * @param studentId 学生ID。
     * @param courseId  课程ID。
     * @return 退课成功返回true，否则返回false。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseHallCache", allEntries = true)
    public boolean dropCourse(String studentId, String courseId) {
        Course course = courseMapper.courseFind(courseId);
        if (course == null) {
            return false;
        }

        int result = courseSelectionMapper.dropCourse(studentId, courseId);

        if (result > 0) {
            course.setCoursePeopleNumber(course.getCoursePeopleNumber() - 1);
            courseMapper.courseUpdate(course);
            return true;
        }

        return false;
    }

    /**
     * 获取学生已选课程列表。
     *
     * @param studentId 学生ID。
     * @return 学生已选课程列表。
     */
    public List<Course> getStudentCourses(String studentId) {
        return courseSelectionMapper.findSelectionsByStudentId(studentId).stream()
                .map(selection -> courseMapper.courseFind(selection.getCourseId()))
                .collect(Collectors.toList());
    }

    /**
     * 检查学生是否已选某课程。
     *
     * @param studentId 学生ID。
     * @param courseId  课程ID。
     * @return 如果已选则返回true，否则返回false。
     */
    public boolean isCourseSelected(String studentId, String courseId) {
        return courseSelectionMapper.checkSelection(studentId, courseId) > 0;
    }

    /**
     * 获取课程的选课人数。
     *
     * @param courseId 课程ID。
     * @return 选课人数。
     */
    public int getCourseEnrollmentCount(String courseId) {
        return courseSelectionMapper.getEnrollmentCount(courseId);
    }

    /**
     * 获取学生课程表（按星期和节数组织）。
     *
     * @param studentId 学生ID。
     * @return 学生课程表。
     */
    public Map<String, Map<String, List<Course>>> getStudentTimetable(String studentId) {
        List<Course> courses = getStudentCourses(studentId);
        return organizeCoursesByPeriod(courses);
    }

    private Map<String, Map<String, List<Course>>> organizeCoursesByPeriod(List<Course> courses) {
        Map<String, Map<String, List<Course>>> timetable = new LinkedHashMap<>();
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String[] periods = {"1-2节", "3-4节", "5-6节", "7-8节", "9-10节", "11-12节"};

        for (String day : days) {
            timetable.put(day, new LinkedHashMap<>());
            for (String period : periods) {
                timetable.get(day).put(period, new ArrayList<>());
            }
        }

        for (Course course : courses) {
            if (course.getCourseTime() != null && !course.getCourseTime().isEmpty()) {
                String[] timeParts = course.getCourseTime().split(",");

                for (String timePart : timeParts) {
                    timePart = timePart.trim();
                    String[] dayAndPeriod = timePart.split(" ");

                    if (dayAndPeriod.length >= 2) {
                        String day = dayAndPeriod[0];
                        String period = dayAndPeriod[1];

                        if (!period.endsWith("节")) {
                            period = period + "节";
                        }

                        if (timetable.containsKey(day) && timetable.get(day).containsKey(period)) {
                            timetable.get(day).get(period).add(course);
                        }
                    }
                }
            }
        }

        return timetable;
    }

    public List<String> checkCourseConflicts(String studentId, String courseId) {
        List<String> conflicts = new ArrayList<>();
        Course newCourse = courseMapper.courseFind(courseId);

        if (newCourse == null || newCourse.getCourseTime() == null) {
            return conflicts;
        }

        List<Course> selectedCourses = getStudentCourses(studentId);
        String[] newCourseTimeParts = newCourse.getCourseTime().split(",");
        Set<String> newCourseTimes = new HashSet<>();

        for (String timePart : newCourseTimeParts) {
            timePart = timePart.trim();
            newCourseTimes.add(timePart);
        }

        for (Course selectedCourse : selectedCourses) {
            if (selectedCourse.getCourseTime() != null) {
                String[] selectedCourseTimeParts = selectedCourse.getCourseTime().split(",");

                for (String selectedTimePart : selectedCourseTimeParts) {
                    selectedTimePart = selectedTimePart.trim();

                    for (String newTimePart : newCourseTimes) {
                        if (newTimePart.equals(selectedTimePart)) {
                            conflicts.add(selectedCourse.getCourseName() + " (" + selectedCourse.getCourseTime() + ")");
                            break;
                        }
                    }

                    if (!conflicts.isEmpty()) {
                        break;
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * 获取可选课程（排除已选和冲突课程）。
     *
     * @param studentId 学生ID。
     * @return 可选课程列表。
     */
    public List<Course> getAvailableCourses(String studentId) {
        List<Course> allCourses = getAllCourses();
        List<Course> selectedCourses = getStudentCourses(studentId);

        return allCourses.stream()
                .filter(course -> selectedCourses.stream()
                        .noneMatch(selected -> selected.getCourseId().equals(course.getCourseId())))
                .collect(Collectors.toList());
    }

    /**
     * 获取推荐课程（基于学生已选课程和专业）。
     *
     * @param studentId 学生ID。
     * @param major     专业名称。
     * @return 推荐课程列表。
     */
    public List<Course> getRecommendedCourses(String studentId, String major) {
        List<Course> availableCourses = getAvailableCourses(studentId);

        if (major != null && !major.isEmpty()) {
            return availableCourses.stream()
                    .filter(course -> course.getCourseName().contains(major) ||
                            (course.getCourseTeacher() != null && course.getCourseTeacher().contains(major)))
                    .collect(Collectors.toList());
        }

        return availableCourses;
    }
}
