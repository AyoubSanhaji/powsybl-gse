/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.gse.app;

import com.powsybl.afs.AppData;
import com.powsybl.afs.Project;
import com.powsybl.afs.ws.client.utils.UserSession;
import com.powsybl.gse.spi.BrandingConfig;
import com.powsybl.gse.spi.GseContext;
import com.powsybl.gse.spi.GseException;
import com.powsybl.gse.util.GseUtil;
import com.powsybl.gse.util.NodeChooser;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Ayoub SANHAJI <sanhaji.ayoub at gmail.com>
 */
public class GsePane extends StackPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(GsePane.class);
    private static final String OPENED_PROJECTS = "openedProjects";
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("lang.GsePane");

    private final GseContext context;

    private final AppData data;

    private final BrandingConfig brandingConfig = BrandingConfig.find();

    private final BorderPane mainPane;
    private final TabPane tabPane = new TabPane();
    private final Preferences preferences;
    private final Application javaxApplication;

    public GsePane(GseContext context, AppData data, Application app) {
        this.context = Objects.requireNonNull(context);
        this.data = Objects.requireNonNull(data);
        this.javaxApplication = Objects.requireNonNull(app);
        mainPane = new BorderPane();
        mainPane.setTop(createAppBar());
        tabPane.getStyleClass().add("gse-tab-pane");
        mainPane.setCenter(tabPane);
        getChildren().addAll(mainPane);

        preferences = Preferences.userNodeForPackage(getClass());

        loadPreferences();
    }

    private void loadPreferences() {
        GseUtil.execute(context.getExecutor(), () -> {
            try {
                List<String> projectPaths = Arrays.asList(preferences.get(OPENED_PROJECTS, "").split(","));
                if (!projectPaths.isEmpty()) {
                    projectPaths.stream()
                            .map(data::getNode)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(Project.class::cast)
                            .filter(project -> !isProjectOpen(project))
                            .forEach(project -> Platform.runLater(() -> addProject(project)));
                }
            } catch (Throwable t) {
                LOGGER.error(t.toString(), t);
            }
        });
    }

    private void savePreferences() {
        List<String> openedProjects = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            openedProjects.add(((ProjectPane) tab).getProject().getPath().toString());
        }
        preferences.put(OPENED_PROJECTS, openedProjects.stream().collect(Collectors.joining(",")));
    }

    private void addProject(Project project) {
        ProjectPane projectPane = new ProjectPane(getScene(), project, context);
        tabPane.getTabs().add(projectPane);
        tabPane.getSelectionModel().select(projectPane);
    }

    private boolean isProjectOpen(Project project) {
        Objects.requireNonNull(project);
        for (Tab tab : tabPane.getTabs()) {
            ProjectPane projectPane = (ProjectPane) tab;
            if (projectPane.getProject().getId().equals(project.getId())) {
                return true;
            }
        }
        return false;
    }

    private void cleanClosedProjects() {
        Iterator<Tab> it = tabPane.getTabs().iterator();
        while (it.hasNext()) {
            ProjectPane projectPane = (ProjectPane) it.next();
            if (projectPane.getProject().getFileSystem().isClosed()) {
                it.remove();
            }
        }
    }

    private void showAbout() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        Pane content = brandingConfig.getAboutPane();
        if (content == null) {
            throw new GseException("Branding about pane is null");
        }
        popup.getContent().addAll(content);
        popup.show(getScene().getWindow());
    }

    private void showShortcuts() {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        HashMap<String, String> shortcutsList = new LinkedHashMap<>();
        shortcutsList.put("CTRL + W", RESOURCE_BUNDLE.getString("CloseFile"));
        shortcutsList.put("CTRL + S", RESOURCE_BUNDLE.getString("Save"));
        shortcutsList.put("CTRL + Z", RESOURCE_BUNDLE.getString("Undo"));
        shortcutsList.put("CTRL + Y", RESOURCE_BUNDLE.getString("Redo"));
        shortcutsList.put("CTRL + X", RESOURCE_BUNDLE.getString("Cut"));
        shortcutsList.put("CTRL + C", RESOURCE_BUNDLE.getString("Copy"));
        shortcutsList.put("CTRL + V", RESOURCE_BUNDLE.getString("Paste"));
        shortcutsList.put("DELETE", RESOURCE_BUNDLE.getString("Delete"));
        shortcutsList.put("CTRL + F", RESOURCE_BUNDLE.getString("Find"));
        shortcutsList.put("CTRL + R", RESOURCE_BUNDLE.getString("Replace"));
        shortcutsList.put("ESC", RESOURCE_BUNDLE.getString("CloseFR"));
        shortcutsList.put("CTRL + A", RESOURCE_BUNDLE.getString("SelectAll"));
        shortcutsList.put("CTRL + DEL", RESOURCE_BUNDLE.getString("DeleteNextWord"));
        shortcutsList.put("CTRL + /", RESOURCE_BUNDLE.getString("CommentLine"));
        shortcutsList.put("CTRL + D", RESOURCE_BUNDLE.getString("DeleteLine"));
        shortcutsList.put("ALT + Down/Up", RESOURCE_BUNDLE.getString("MoveLine"));
        shortcutsList.put("CTRL + SHIFT + Down/Up", RESOURCE_BUNDLE.getString("DuplicateLW"));

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("shortcuts");
        Iterator it = shortcutsList.entrySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            Text text = new Text((String) pair.getKey());
            text.setStyle("-fx-font-weight: bold;");
            gridPane.add(text, 0, i);
            gridPane.add(new Text((String) pair.getValue()), 1, i);
            i++;
        }
        Pane pane = new Pane(gridPane);
        popup.getContent().addAll(pane);
        popup.show(getScene().getWindow());
    }

    private void setUserSession(UserSession userSession) {
        data.setTokenProvider(() -> userSession != null ? userSession.getToken() : null);
    }

    private GseAppBar createAppBar() {
        GseAppBar appBar = new GseAppBar(context, brandingConfig);
        if (appBar.getUserSessionPane() != null) {
            appBar.getUserSessionPane().sessionProperty().addListener((observable, oldUserSession, newUserSession) -> {
                setUserSession(newUserSession);
                if (newUserSession != null) {
                    loadPreferences();
                } else {
                    // clean remote projects
                    cleanClosedProjects();
                }
            });
        }
        appBar.getCreateButton().setOnAction(event -> {
            Optional<Project> project = NewProjectPane.showAndWaitDialog(getScene().getWindow(), data, context);
            project.ifPresent(this::addProject);
        });

        appBar.getOpenButton().setOnAction(event -> {
            Set<String> openedProjects = new HashSet<>();
            for (Tab tab : tabPane.getTabs()) {
                openedProjects.add(((ProjectPane) tab).getProject().getId());
            }
            Optional<Project> project = NodeChooser.showAndWaitDialog(getScene().getWindow(), data, context, Project.class, openedProjects);
            project.ifPresent(this::openProject);
        });

        ContextMenu contextMenu = new ContextMenu();

        brandingConfig.getDocumentation(javaxApplication).ifPresent(p -> {
            MenuItem documentationMenuItem = new MenuItem(RESOURCE_BUNDLE.getString("Documentation") + "...");
            documentationMenuItem.setOnAction(event -> p.show());
            contextMenu.getItems().add(documentationMenuItem);
        });

        MenuItem aboutMenuItem = new MenuItem(RESOURCE_BUNDLE.getString("About"));
        MenuItem shortcutMenuItem = new MenuItem(RESOURCE_BUNDLE.getString("Shortcuts"));
        aboutMenuItem.setOnAction(event -> showAbout());
        shortcutMenuItem.setOnAction(event -> showShortcuts());
        contextMenu.getItems().add(aboutMenuItem);
        contextMenu.getItems().add(shortcutMenuItem);

        appBar.getHelpButton().setOnAction(event -> contextMenu.show(appBar.getHelpButton(), Side.BOTTOM, 0, 0));
        return appBar;
    }

    private void openProject(Project project) {
        if (!isProjectOpen(project)) {
            addProject(project);
        } else {
            for (Tab tab : tabPane.getTabs()) {
                ProjectPane projectPane = (ProjectPane) tab;
                if (projectPane.getProject().getId().equals(project.getId())) {
                    tab.getTabPane().getSelectionModel().select(tab);
                    break;
                }
            }
        }
    }

    public String getTitle() {
        String title = brandingConfig.getTitle();
        Objects.requireNonNull(title, "Branding title is null");
        return title;
    }

    public List<Image> getIcons() {
        return brandingConfig.getIcons();
    }

    public void dispose() {
        savePreferences();
        for (Tab tab : tabPane.getTabs()) {
            ((ProjectPane) tab).dispose();
        }
    }

    public boolean isClosable() {
        for (Tab tab : tabPane.getTabs()) {
            if (!(((ProjectPane) tab).canBeClosed())) {
                return false;
            }
        }
        return true;
    }
}
