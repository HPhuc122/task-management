package com.taskmanagement.service;

import com.taskmanagement.dto.ProjectRequest;
import com.taskmanagement.dto.ProjectResponse;
import com.taskmanagement.entity.Project;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projects;
    private final UserRepository users;

    public ProjectService(ProjectRepository projects, UserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    public List<ProjectResponse> findAll() {
        return projects.findAll(Sort.by("id")).stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse findById(Long id) {
        return ProjectResponse.from(requireProject(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project();
        apply(project, request);
        return ProjectResponse.from(projects.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = requireProject(id);
        apply(project, request);
        return ProjectResponse.from(projects.saveAndFlush(project));
    }

    @Transactional
    public void delete(Long id) {
        projects.delete(requireProject(id));
        projects.flush();
    }

    private Project requireProject(Long id) {
        return projects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    private void apply(Project project, ProjectRequest request) {
        project.setUser(users.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId())));
        project.setName(request.name().strip());
        project.setDescription(request.description());
    }
}
