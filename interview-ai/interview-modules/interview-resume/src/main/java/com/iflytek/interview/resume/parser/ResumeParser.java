package com.iflytek.interview.resume.parser;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.resume.vo.ResumeParseResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeParser {

    private static final String[] SKILL_KEYWORDS = {
            "Java", "Spring", "Spring Boot", "MySQL", "Redis",
            "Vue", "Python", "Docker", "Linux", "NLP"
    };

    private static final List<String> SECTION_HEADERS = List.of(
            "教育经历", "工作经历", "项目经历", "项目经验",
            "技能", "自我评价", "个人简介", "求职意向"
    );

    public String extractText(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (Exception e) {
            throw new BusinessException(400, "简历文本提取失败");
        }
    }

    public ResumeParseResult parse(String text) {
        ResumeParseResult result = new ResumeParseResult();
        result.setName(extractName(text));
        result.setPhone(extractPhone(text));
        result.setEmail(extractEmail(text));
        result.setEducationList(extractEducation(text));
        result.setSkills(extractSkills(text));
        result.setProjects(extractProjects(text));
        result.setKeywords(mergeKeywords(result.getSkills(), result.getEducationList(), result.getProjects()));
        return result;
    }

    private String extractName(String text) {
        Matcher matcher = Pattern.compile("(姓名|Name)[:：]\\s*([\\u4e00-\\u9fa5]{2,4})").matcher(text);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private String extractPhone(String text) {
        Matcher matcher = Pattern.compile("1[3-9]\\d{9}").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractEmail(String text) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private List<String> extractEducation(String text) {
        List<String> education = new ArrayList<>();
        Pattern degree = Pattern.compile(
                "([\\u4e00-\\u9fa5]{2,20}(大学|学院|学校))[^\\n\\r]{0,40}(本科|硕士|博士|研究生|学士|专科|大专)");
        Matcher m = degree.matcher(text);
        while (m.find()) {
            education.add(m.group().replaceAll("\\s+", " ").trim());
        }
        if (education.isEmpty()) {
            Matcher m2 = Pattern.compile("[\\u4e00-\\u9fa5]{2,20}(大学|学院|学校)").matcher(text);
            while (m2.find()) {
                education.add(m2.group());
            }
        }
        return education;
    }

    private List<String> extractSkills(String text) {
        List<String> skills = new ArrayList<>();
        for (String keyword : SKILL_KEYWORDS) {
            if (text.toLowerCase().contains(keyword.toLowerCase())) {
                skills.add(keyword);
            }
        }
        return skills;
    }

    private List<String> extractProjects(String text) {
        String[] lines = text.split("\\r?\\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.equals("项目经历") || t.equals("项目经验") || t.startsWith("项目经历:") || t.startsWith("项目经验:")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return List.of();
        }
        List<String> projects = new ArrayList<>();
        for (int i = start; i < lines.length && projects.size() < 5; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            if (isSectionHeader(t)) {
                break;
            }
            projects.add(t);
        }
        return projects;
    }

    private boolean isSectionHeader(String line) {
        for (String header : SECTION_HEADERS) {
            if (line.equals(header) || line.startsWith(header + ":") || line.startsWith(header + "：")) {
                return true;
            }
        }
        return false;
    }

    private List<String> mergeKeywords(List<String> skills, List<String> education, List<String> projects) {
        List<String> keywords = new ArrayList<>(skills);
        for (String s : education) {
            if (!keywords.contains(s)) {
                keywords.add(s);
            }
        }
        for (String p : projects) {
            if (!keywords.contains(p)) {
                keywords.add(p);
            }
        }
        return keywords;
    }
}
