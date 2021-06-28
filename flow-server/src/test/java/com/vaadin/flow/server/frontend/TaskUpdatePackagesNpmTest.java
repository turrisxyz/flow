/*
 * Copyright 2000-2021 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.flow.server.frontend;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import com.vaadin.flow.server.Constants;
import com.vaadin.flow.server.frontend.scanner.ClassFinder;
import com.vaadin.flow.server.frontend.scanner.FrontendDependencies;

import elemental.json.Json;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

import static com.vaadin.flow.server.Constants.PACKAGE_JSON;
import static com.vaadin.flow.server.frontend.NodeUpdater.DEPENDENCIES;
import static com.vaadin.flow.server.frontend.NodeUpdater.DEV_DEPENDENCIES;
import static com.vaadin.flow.server.frontend.NodeUpdater.VAADIN_DEP_KEY;

@NotThreadSafe
public class TaskUpdatePackagesNpmTest {

    private static final String PLATFORM_DIALOG_VERSION = "2.5.2";
    private static final String VAADIN_ELEMENT_MIXIN = "@vaadin/vaadin-element-mixin";
    private static final String VAADIN_DIALOG = "@vaadin/vaadin-dialog";
    private static final String VAADIN_OVERLAY = "@vaadin/vaadin-overlay";
    private static final String PLATFORM_ELEMENT_MIXIN_VERSION = "2.4.2";
    private static final String PLATFORM_OVERLAY_VERSION = "3.5.1";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File npmFolder;

    private ClassFinder finder;

    private File generatedPath;

    private File versionJsonFile;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        npmFolder = temporaryFolder.newFolder();
        generatedPath = new File(npmFolder, "generated");
        generatedPath.mkdir();
        versionJsonFile = new File(npmFolder, "versions.json");
        finder = Mockito.mock(ClassFinder.class);
        Mockito.when(finder.getResource(Constants.VAADIN_VERSIONS_JSON))
                .thenReturn(versionJsonFile.toURI().toURL());
    }

    @Test
    public void passUnorderedApplicationDependenciesAndReadUnorderedPackageJson_resultingPackageJsonIsOrdered()
            throws IOException {
        createBasicVaadinVersionsJson();

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("foo", "bar");
        // "bar" is lexicographically before the "foo" but in the linked hash
        // map it's set after
        map.put("baz", "foobar");

        JsonObject packageJson = getOrCreatePackageJson();
        JsonObject dependencies = packageJson.getObject(DEPENDENCIES);

        packageJson.remove(DEPENDENCIES);

        packageJson.put("name", "a");
        packageJson.put("license", "b");
        packageJson.put("version", "c");

        LinkedHashSet<String> mainKeys = new LinkedHashSet<>(
                Arrays.asList(packageJson.keys()));

        packageJson.put(DEPENDENCIES, dependencies);

        // Json object preserve the order of keys
        dependencies.put("foo-pack", "bar");
        dependencies.put("baz-pack", "foobar");
        FileUtils.writeStringToFile(new File(npmFolder, PACKAGE_JSON),
                packageJson.toJson(), StandardCharsets.UTF_8);

        TaskUpdatePackages task = createTask(map);

        task.execute();

        // now read the package json file
        packageJson = getOrCreatePackageJson();

        List<String> list = Arrays.asList(packageJson.keys());
        // the "vaadin" key is the last one
        Assert.assertEquals(list.size() - 1, list.indexOf(VAADIN_DEP_KEY));

        List<String> keysBeforeDeps = new ArrayList<>();

        for (String key : packageJson.keys()) {
            if (key.equals(DEV_DEPENDENCIES) || key.equals(DEPENDENCIES)) {
                break;
            }
            if (mainKeys.contains(key)) {
                keysBeforeDeps.add(key);
            }
        }

        // the order of the main keys is the same
        Assert.assertArrayEquals(mainKeys.toArray(), keysBeforeDeps.toArray());

        checkOrder(DEPENDENCIES, packageJson.getObject(DEPENDENCIES));
        checkOrder(DEV_DEPENDENCIES, packageJson.getObject(DEV_DEPENDENCIES));
        checkOrder(VAADIN_DEP_KEY, packageJson.getObject(VAADIN_DEP_KEY));
    }

    private void checkOrder(String path, JsonObject object) {
        String[] keys = object.keys();
        if (path.isEmpty()) {
            Assert.assertTrue("Keys in the package Json are not sorted",
                    isSorted(keys));
        } else {
            Assert.assertTrue(
                    "Keys for the object " + path
                            + " in the package Json are not sorted",
                    isSorted(keys));
        }
        for (String key : keys) {
            JsonValue value = object.get(key);
            if (value instanceof JsonObject) {
                checkOrder(path + "/" + key, (JsonObject) value);
            }
        }
    }

    private boolean isSorted(String[] array) {
        if (array.length < 2) {
            return true;
        }
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i].compareTo(array[i + 1]) > 0) {
                return false;
            }
        }
        return true;
    }

    private void createBasicVaadinVersionsJson() {
        createVaadinVersionsJson(PLATFORM_DIALOG_VERSION,
                PLATFORM_ELEMENT_MIXIN_VERSION, PLATFORM_OVERLAY_VERSION);
    }

    private void createVaadinVersionsJson(String dialogVersion,
            String elementMixinVersion, String overlayVersion) {
        // testing with exact versions json content instead of mocking parsing
        String versionJsonString = //@formatter:off
                "{ \"core\": {"
                        + "\"vaadin-dialog\": {\n"
                        + "   \"component\": true,\n"
                        + "   \"javaVersion\": \"{{version}}\",\n"
                        + "    \"jsVersion\": \""+dialogVersion+"\",\n"
                        + "    \"npmName\": \""+VAADIN_DIALOG+"\"\n"
                        + "},\n"
                        + "\"vaadin-element-mixin\": {\n"
                        + "    \"jsVersion\": \""+elementMixinVersion+"\",\n"
                        + "    \"npmName\": \""+VAADIN_ELEMENT_MIXIN+"\"\n"
                        + "},\n"
                        + "\"vaadin-overlay\": {\n"
                        + "    \"jsVersion\": \""+overlayVersion+"\",\n"
                        + "    \"npmName\": \""+VAADIN_OVERLAY+"\",\n"
                        + "    \"releasenotes\": true\n"
                        +"}}},\n";//@formatter:on
        try {
            FileUtils.write(versionJsonFile, versionJsonString,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TaskUpdatePackages createTask(
            Map<String, String> applicationDependencies) {
        return createTask(applicationDependencies, false);
    }

    private TaskUpdatePackages createTask(
            Map<String, String> applicationDependencies, boolean enablePnpm) {
        final FrontendDependencies frontendDependenciesScanner = Mockito
                .mock(FrontendDependencies.class);
        Mockito.when(frontendDependenciesScanner.getPackages())
                .thenReturn(applicationDependencies);
        return new TaskUpdatePackages(finder, frontendDependenciesScanner,
                npmFolder, generatedPath, null, false, enablePnpm) {
        };
    }

    private JsonObject getOrCreatePackageJson() throws IOException {
        File packageJson = new File(npmFolder, PACKAGE_JSON);
        if (packageJson.exists())
            return Json.parse(FileUtils.readFileToString(packageJson,
                    StandardCharsets.UTF_8));
        else {
            final JsonObject packageJsonJson = Json.createObject();
            packageJsonJson.put(DEPENDENCIES, Json.createObject());
            FileUtils.writeStringToFile(new File(npmFolder, PACKAGE_JSON),
                    packageJsonJson.toJson(), StandardCharsets.UTF_8);
            return packageJsonJson;
        }
    }

}
