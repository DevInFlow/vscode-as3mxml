/*
Copyright 2016-2021 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.as3mxml.vscode.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import com.as3mxml.vscode.asdoc.VSCodeASDocComment;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IDocumentableDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.swc.ISWC;
import org.apache.royale.swc.dita.IDITAList;

public class DefinitionDocumentationUtils {
    private static final String ASDOC_TAG_PARAM = "param";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";

    public static String getDocumentationForDefinition(IDefinition definition, boolean useMarkdown,
            IWorkspace workspace, boolean allowDITA) {
        if (!(definition instanceof IDocumentableDefinition)) {
            return null;
        }
        IDocumentableDefinition documentableDefinition = (IDocumentableDefinition) definition;
        VSCodeASDocComment comment = getCommentForDefinition(documentableDefinition, useMarkdown, workspace, allowDITA);
        if (comment == null) {
            return null;
        }
        comment.compile(useMarkdown);
        String description = comment.getDescription();
        if (description == null) {
            return null;
        }
        return description;
    }

    public static String getDocumentationForParameter(IParameterDefinition definition, boolean useMarkdown,
            IWorkspace workspace) {
        IDefinition parentDefinition = definition.getParent();
        if (!(parentDefinition instanceof IFunctionDefinition)) {
            return null;
        }
        IFunctionDefinition functionDefinition = (IFunctionDefinition) parentDefinition;
        VSCodeASDocComment comment = getCommentForDefinition(functionDefinition, useMarkdown, workspace, true);
        if (comment == null) {
            return null;
        }
        comment.compile(useMarkdown);
        Collection<IASDocTag> paramTags = comment.getTagsByName(ASDOC_TAG_PARAM);
        if (paramTags == null) {
            return null;
        }
        String paramName = definition.getBaseName();
        for (IASDocTag paramTag : paramTags) {
            String description = paramTag.getDescription();
            if (description.startsWith(paramName + " ")) {
                return description.substring(paramName.length() + 1);
            }
        }
        return null;
    }

    private static VSCodeASDocComment getCommentForDefinition(IDocumentableDefinition documentableDefinition,
            boolean useMarkdown, IWorkspace workspace, boolean allowDITA) {
        VSCodeASDocComment comment = (VSCodeASDocComment) documentableDefinition.getExplicitSourceComment();
        String definitionFilePath = documentableDefinition.getContainingFilePath();
        if (allowDITA && comment == null && definitionFilePath != null && definitionFilePath.endsWith(".swc")) {
            IDITAList ditaList = null;
            File swcFile = new File(definitionFilePath);
            if (swcFile.exists()) {
                // first try to find the asdoc in the .swc
                ISWC swc = workspace.getSWCManager().get(swcFile);
                String fileName = swcFile.getName();
                ditaList = swc.getDITAList();

                // next, if it's a SDK/framework liberary, check for an
                // associated resource bundle .swc
                if (ditaList == null && (definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                        || definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS))) {
                    String rbName = fileName.substring(0, fileName.length() - 4) + "_rb.swc";
                    File frameworksDir = swcFile.getParentFile();
                    while (!frameworksDir.getName().equals("frameworks")) {
                        frameworksDir = frameworksDir.getParentFile();
                    }
                    File rbSwcFile = new File(frameworksDir, "locale/en_US/" + rbName);
                    if (rbSwcFile.exists()) {
                        ISWC rbSwc = workspace.getSWCManager().get(rbSwcFile);
                        ditaList = rbSwc.getDITAList();
                    }
                }
                // finally, fall back to the bundled documentation for
                // playerglobal or airglobal, if the filename matches
                if (ditaList == null && (fileName.contains("playerglobal") || fileName.contains("airglobal"))) {
                    try {
                        File jarPath = new File(DefinitionDocumentationUtils.class.getProtectionDomain().getCodeSource()
                                .getLocation().toURI());
                        File packageDitaFile = new File(jarPath.getParentFile().getParentFile(),
                                "playerglobal_docs/packages.dita");
                        FileInputStream packageDitaStream = new FileInputStream(packageDitaFile);
                        ditaList = workspace.getASDocDelegate().getPackageDitaParser().parse(definitionFilePath,
                                packageDitaStream);
                        try {
                            packageDitaStream.close();
                        } catch (IOException e) {
                        }
                    } catch (URISyntaxException e) {
                        return null;
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                }
            }
            if (ditaList == null) {
                return null;
            }
            try {
                comment = (VSCodeASDocComment) ditaList.getComment(documentableDefinition);
            } catch (Exception e) {
                return null;
            }
        }
        return comment;
    }
}