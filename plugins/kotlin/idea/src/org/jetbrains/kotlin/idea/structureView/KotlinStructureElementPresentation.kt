// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.LocationPresentation
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.util.PsiIconUtil
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.idea.KotlinIdeaBundle
import org.jetbrains.kotlin.idea.projectView.KtDeclarationTreeNode.Companion.tryGetRepresentableText
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.DescriptorRenderer.Companion.ONLY_NAMES_WITH_SHORT_TYPES
import org.jetbrains.kotlin.resolve.DescriptorUtils.getAllOverriddenDeclarations
import org.jetbrains.kotlin.resolve.OverridingUtil.filterOutOverridden
import javax.swing.Icon

internal class KotlinStructureElementPresentation(
    private val isInherited: Boolean,
    navigatablePsiElement: NavigatablePsiElement,
    descriptor: DeclarationDescriptor?
) : ColoredItemPresentation, LocationPresentation {
    private val attributesKey = getElementAttributesKey(isInherited, navigatablePsiElement)
    private val elementText = getElementText(navigatablePsiElement, descriptor)
    private val locationString = getElementLocationString(isInherited, descriptor)
    private val icon = getElementIcon(navigatablePsiElement, descriptor)

    override fun getTextAttributesKey() = attributesKey

    override fun getPresentableText() = elementText

    override fun getLocationString() = locationString

    override fun getIcon(unused: Boolean) = icon

    override fun getLocationPrefix(): String {
        return if (isInherited) " " else LocationPresentation.DEFAULT_LOCATION_PREFIX
    }

    override fun getLocationSuffix(): String {
        return if (isInherited) "" else LocationPresentation.DEFAULT_LOCATION_SUFFIX
    }

    private fun getElementAttributesKey(isInherited: Boolean, navigatablePsiElement: NavigatablePsiElement): TextAttributesKey? {
        if (isInherited) {
            return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES
        }

        if (navigatablePsiElement is KtModifierListOwner && KtPsiUtil.isDeprecated(navigatablePsiElement)) {
            return CodeInsightColors.DEPRECATED_ATTRIBUTES
        }

        return null
    }

    private fun getElementIcon(navigatablePsiElement: NavigatablePsiElement, descriptor: DeclarationDescriptor?): Icon? {
        if (descriptor != null) {
            return KotlinDescriptorIconProvider.getIcon(descriptor, navigatablePsiElement, Iconable.ICON_FLAG_VISIBILITY)
        }

        if (!navigatablePsiElement.isValid) {
            return null
        }

        return PsiIconUtil.getProvidersIcon(navigatablePsiElement, Iconable.ICON_FLAG_VISIBILITY)
    }

    private fun getElementText(navigatablePsiElement: NavigatablePsiElement, descriptor: DeclarationDescriptor?): String? {
        if (navigatablePsiElement is KtObjectDeclaration && navigatablePsiElement.isObjectLiteral()) {
            return KotlinIdeaBundle.message("object.0", (navigatablePsiElement.getSuperTypeList()?.text?.let { " : $it" } ?: ""))
        }

        if (descriptor != null) {
            return ONLY_NAMES_WITH_SHORT_TYPES.render(descriptor)
        }

        navigatablePsiElement.name.takeUnless { it.isNullOrEmpty() }?.let { return it }

        return (navigatablePsiElement as? KtDeclaration)?.let(::tryGetRepresentableText)
    }

    private fun getElementLocationString(isInherited: Boolean, descriptor: DeclarationDescriptor?): String? {
        if (!(isInherited && descriptor is CallableMemberDescriptor)) return null

        if (descriptor.kind == CallableMemberDescriptor.Kind.DECLARATION) {
            return withRightArrow(ONLY_NAMES_WITH_SHORT_TYPES.render(descriptor.containingDeclaration))
        }

        val overridingDescriptors = filterOutOverridden(getAllOverriddenDeclarations(descriptor))
        // Location can be missing when base in synthesized
        return overridingDescriptors.firstOrNull()?.let {
            withRightArrow(ONLY_NAMES_WITH_SHORT_TYPES.render(it.containingDeclaration))
        }
    }

    private fun withRightArrow(str: String): String {
        val rightArrow = '\u2192'
        return if (StartupUiUtil.getLabelFont().canDisplay(rightArrow)) rightArrow + str else "->" + str
    }
}
