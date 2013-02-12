/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.List;

/**
 * @author yole
 */
public class RegisterExtensionFix implements IntentionAction {
  private final PsiClass myExtensionClass;
  private final List<ExtensionPointCandidate> myEPCandidates;

  public RegisterExtensionFix(PsiClass extensionClass, List<ExtensionPointCandidate> epCandidates) {
    myExtensionClass = extensionClass;
    myEPCandidates = epCandidates;
  }

  @NotNull
  @Override
  public String getText() {
    return "Register extension";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    RegisterInspectionFix.choosePluginDescriptor(project, editor, file, new Consumer<DomFileElement<IdeaPlugin>>() {
      @Override
      public void consume(DomFileElement<IdeaPlugin> element) {
        doFix(element);
      }
    });
  }

  private void doFix(final DomFileElement<IdeaPlugin> element) {
    if (myEPCandidates.size() == 1) {
      registerExtension(element, myEPCandidates.get(0));
    }
  }

  private void registerExtension(final DomFileElement<IdeaPlugin> element, final ExtensionPointCandidate candidate) {
    PsiElement navTarget = new WriteCommandAction<PsiElement>(element.getFile().getProject(), element.getFile()) {
      @Override
      protected void run(Result<PsiElement> result) throws Throwable {
        Extensions extensions = RegisterInspectionFix.getExtension(element.getRootElement(), candidate.epName);
        Extension extension = extensions.addExtension(candidate.epName);
        XmlTag tag = extension.getXmlTag();
        PsiElement navTarget = null;
        if (KeyedFactoryEPBean.class.getName().equals(candidate.beanClassName) ||
            KeyedLazyInstanceEP.class.getName().equals(candidate.beanClassName)) {
          XmlAttribute attr = tag.setAttribute("key", "");
          navTarget = attr.getValueElement();
        }
        else if (LanguageExtensionPoint.class.getName().equals(candidate.beanClassName)) {
          XmlAttribute attr = tag.setAttribute("language", "");
          navTarget = attr.getValueElement();
        }
        tag.setAttribute(candidate.attributeName, myExtensionClass.getQualifiedName());
        result.setResult(navTarget != null ? navTarget : extension.getXmlTag());
      }
    }.execute().throwException().getResultObject();
    PsiNavigateUtil.navigate(navTarget);
  }


  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
