/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#define LOG_TAG "vulkan"

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR
#endif

#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <array>
#include <cstring>
#include <vector>

#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct VulkanDeviceInfo {
    VkInstance instance;
    VkPhysicalDevice gpu;
    VkSurfaceKHR surface;
    uint32_t queueFamilyIndex;
    VkDevice device;
    VkQueue queue;
};

struct VulkanSwapchainInfo {
    VkFormat displayFormat;
    VkExtent2D displaySize;
    VkSwapchainKHR swapchain;
    uint32_t imageCount;

    std::vector<VkImageView> imageViews;
};

struct VulkanBufferInfo {
    VkDeviceMemory memory;
    VkBuffer vertexBuffer;
};

struct VulkanPipelineInfo {
    VkPipelineLayout layout;
    VkPipelineCache cache;
    VkPipeline pipeline;
};

struct VulkanRenderInfo {
    VkRenderPass renderPass;
    VkCommandPool commandPool;
    uint32_t commandBufferLength;
    VkSemaphore semaphore;
    VkFence fence;

    std::vector<VkFramebuffer> framebuffers;
    std::vector<VkCommandBuffer> commandBuffers;
};

struct VulkanInfo {
    VulkanInfo() {
        memset(&deviceInfo, 0, sizeof(deviceInfo));
        memset(&swapchainInfo, 0, sizeof(swapchainInfo));
        memset(&bufferInfo, 0, sizeof(bufferInfo));
        memset(&pipelineInfo, 0, sizeof(pipelineInfo));
        memset(&renderInfo, 0, sizeof(renderInfo));
    }
    VulkanDeviceInfo deviceInfo;
    VulkanSwapchainInfo swapchainInfo;
    VulkanBufferInfo bufferInfo;
    VulkanPipelineInfo pipelineInfo;
    VulkanRenderInfo renderInfo;
};

const float vertexData[] = {
        // Vertices for top 2 rects
        -1.0f, -1.0f, 0.0f,
        -1.0f,  0.0f, 0.0f,
         0.0f, -1.0f, 0.0f,
         0.0f,  0.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
         1.0f,  0.0f, 0.0f,
        // Vertices for bottom 2 rects
        -1.0f,  0.0f, 0.0f,
        -1.0f,  1.0f, 0.0f,
         0.0f,  0.0f, 0.0f,
         0.0f,  1.0f, 0.0f,
         1.0f,  0.0f, 0.0f,
         1.0f,  1.0f, 0.0f,
};

const float fragData[] = {
        1.0f, 0.0f, 0.0f, // Red
        0.0f, 1.0f, 0.0f, // Green
        0.0f, 0.0f, 1.0f, // Blue
        1.0f, 1.0f, 0.0f, // Yellow
};

static const char* requiredInstanceExtensions[] = {
        "VK_KHR_surface",
        "VK_KHR_android_surface",
        "VK_KHR_get_surface_capabilities2",
        "VK_KHR_get_physical_device_properties2",
};

static const char* requiredDeviceExtensions[] = {
        "VK_KHR_swapchain",
};

static bool enumerateInstanceExtensions(std::vector<VkExtensionProperties>* extensions) {
    VkResult result;

    uint32_t count = 0;
    result = vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr);
    if (result != VK_SUCCESS) return false;

    extensions->resize(count);
    result = vkEnumerateInstanceExtensionProperties(nullptr, &count, extensions->data());
    if (result != VK_SUCCESS) return false;

    return true;
}

static bool enumerateDeviceExtensions(VkPhysicalDevice device,
                                      std::vector<VkExtensionProperties>* extensions) {
    VkResult result;

    uint32_t count = 0;
    result = vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr);
    if (result != VK_SUCCESS) return false;

    extensions->resize(count);
    result = vkEnumerateDeviceExtensionProperties(device, nullptr, &count, extensions->data());
    if (result != VK_SUCCESS) return false;

    return true;
}

static bool hasExtension(const char* extension_name,
                         const std::vector<VkExtensionProperties>& extensions) {
    return std::find_if(extensions.cbegin(), extensions.cend(),
                        [extension_name](const VkExtensionProperties& extension) {
                            return strcmp(extension.extensionName, extension_name) == 0;
                        }) != extensions.cend();
}

static void releaseVulkan(VulkanInfo& vulkanInfo) {
    VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;
    VulkanSwapchainInfo& swapchainInfo = vulkanInfo.swapchainInfo;
    VulkanBufferInfo& bufferInfo = vulkanInfo.bufferInfo;
    VulkanPipelineInfo& pipelineInfo = vulkanInfo.pipelineInfo;
    VulkanRenderInfo& renderInfo = vulkanInfo.renderInfo;

    if (deviceInfo.device != VK_NULL_HANDLE) {
        if (vkDeviceWaitIdle(deviceInfo.device) < 0) {
            ALOGE("Failed to wait until device idle");
        }
        vkDestroyFence(deviceInfo.device, renderInfo.fence, nullptr);
        vkDestroySemaphore(deviceInfo.device, renderInfo.semaphore, nullptr);
        if (renderInfo.commandBufferLength > 0) {
            vkFreeCommandBuffers(deviceInfo.device, renderInfo.commandPool,
                                 renderInfo.commandBufferLength, renderInfo.commandBuffers.data());
        }
        vkDestroyCommandPool(deviceInfo.device, renderInfo.commandPool, nullptr);
        vkDestroyPipeline(deviceInfo.device, pipelineInfo.pipeline, nullptr);
        vkDestroyPipelineCache(deviceInfo.device, pipelineInfo.cache, nullptr);
        vkDestroyPipelineLayout(deviceInfo.device, pipelineInfo.layout, nullptr);
        vkDestroyBuffer(deviceInfo.device, bufferInfo.vertexBuffer, nullptr);
        vkFreeMemory(deviceInfo.device, bufferInfo.memory, nullptr);
        vkDestroyRenderPass(deviceInfo.device, renderInfo.renderPass, nullptr);
        for (auto& framebuffer : renderInfo.framebuffers) {
            vkDestroyFramebuffer(deviceInfo.device, framebuffer, nullptr);
        }
        for (auto& imageView : swapchainInfo.imageViews) {
            vkDestroyImageView(deviceInfo.device, imageView, nullptr);
        }
        vkDestroySwapchainKHR(deviceInfo.device, swapchainInfo.swapchain, nullptr);
        vkDestroyDevice(deviceInfo.device, nullptr);
        deviceInfo.device = VK_NULL_HANDLE;
    }

    if (deviceInfo.instance != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(deviceInfo.instance, deviceInfo.surface, nullptr);
        vkDestroyInstance(deviceInfo.instance, nullptr);
        deviceInfo.instance = VK_NULL_HANDLE;
    }
}

static int createVulkanDevice(VulkanInfo& vulkanInfo, ANativeWindow* window) {
    VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;

    VkResult result;

    std::vector<VkExtensionProperties> supportedInstanceExtensions;
    if (!enumerateInstanceExtensions(&supportedInstanceExtensions)) {
        ALOGE("Failed to enumerate instance extensions");
        return -1;
    }

    std::vector<const char*> enabledInstanceExtensions;
    for (const auto extension : requiredInstanceExtensions) {
        if (hasExtension(extension, supportedInstanceExtensions)) {
            enabledInstanceExtensions.push_back(extension);
        } else {
            ALOGE("Missing support for extension: %s", extension);
            return -1;
        }
    }

    const VkApplicationInfo appInfo = {
            .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
            .pNext = nullptr,
            .pApplicationName = "VulkanPreTransform",
            .applicationVersion = 1,
            .pEngineName = "",
            .engineVersion = 0,
            .apiVersion = VK_API_VERSION_1_0,
    };
    const VkInstanceCreateInfo instanceInfo = {
            .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .pApplicationInfo = &appInfo,
            .enabledLayerCount = 0,
            .ppEnabledLayerNames = nullptr,
            .enabledExtensionCount = static_cast<uint32_t>(enabledInstanceExtensions.size()),
            .ppEnabledExtensionNames = enabledInstanceExtensions.data(),
    };
    result = vkCreateInstance(&instanceInfo, nullptr, &deviceInfo.instance);
    if (result < 0) {
        ALOGE("Failed to create VkInstance err(%d)", result);
        return -1;
    }

    uint32_t gpuCount = 0;
    result = vkEnumeratePhysicalDevices(deviceInfo.instance, &gpuCount, nullptr);
    if (result < 0) {
        ALOGE("Failed to enumerate physical devices count(%d) err(%d)", gpuCount, result);
        return -1;
    }
    if (gpuCount == 0) {
        ALOGD("No physical devices available");
        return 1;
    }

    std::vector<VkPhysicalDevice> gpus(gpuCount, VK_NULL_HANDLE);
    result = vkEnumeratePhysicalDevices(deviceInfo.instance, &gpuCount, gpus.data());
    if (result < 0) {
        ALOGE("Failed to enumerate physical devices err(%d)", result);
        return -1;
    }

    deviceInfo.gpu = gpus[0];

    const VkAndroidSurfaceCreateInfoKHR surfaceInfo = {
            .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
            .pNext = nullptr,
            .flags = 0,
            .window = window,
    };
    result = vkCreateAndroidSurfaceKHR(deviceInfo.instance, &surfaceInfo, nullptr,
                                       &deviceInfo.surface);
    if (result < 0) {
        ALOGE("Failed to create Android surface from ANativeWindow* err(%d)", result);
        return -1;
    }

    std::vector<VkExtensionProperties> supportedDeviceExtensions;
    if (!enumerateDeviceExtensions(deviceInfo.gpu, &supportedDeviceExtensions)) {
        ALOGE("Failed to enumerate device extensions");
        return -1;
    }

    std::vector<const char*> enabledDeviceExtensions;
    for (const auto extension : requiredDeviceExtensions) {
        if (hasExtension(extension, supportedDeviceExtensions)) {
            enabledDeviceExtensions.push_back(extension);
        } else {
            ALOGE("Missing support for extension: %s", extension);
            return -1;
        }
    }

    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(deviceInfo.gpu, &queueFamilyCount, nullptr);
    if (!queueFamilyCount) {
        ALOGE("Queue family count is Zero");
        return -1;
    }

    std::vector<VkQueueFamilyProperties> queueFamilyProperties(queueFamilyCount);
    vkGetPhysicalDeviceQueueFamilyProperties(deviceInfo.gpu, &queueFamilyCount,
                                             queueFamilyProperties.data());

    uint32_t queueFamilyIndex;
    for (queueFamilyIndex = 0; queueFamilyIndex < queueFamilyCount; ++queueFamilyIndex) {
        if (queueFamilyProperties[queueFamilyIndex].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            break;
        }
    }
    if (queueFamilyIndex == queueFamilyCount) {
        ALOGE("VK_QUEUE_GRAPHICS_BIT not supported by any queue family");
        return -1;
    }
    deviceInfo.queueFamilyIndex = queueFamilyIndex;

    const float priority = 1.0f;
    const VkDeviceQueueCreateInfo queueCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .queueFamilyIndex = deviceInfo.queueFamilyIndex,
            .queueCount = 1,
            .pQueuePriorities = &priority,
    };
    const VkDeviceCreateInfo deviceCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
            .pNext = nullptr,
            .queueCreateInfoCount = 1,
            .pQueueCreateInfos = &queueCreateInfo,
            .enabledLayerCount = 0,
            .ppEnabledLayerNames = nullptr,
            .enabledExtensionCount = static_cast<uint32_t>(enabledDeviceExtensions.size()),
            .ppEnabledExtensionNames = enabledDeviceExtensions.data(),
            .pEnabledFeatures = nullptr,
    };
    result = vkCreateDevice(deviceInfo.gpu, &deviceCreateInfo, nullptr, &deviceInfo.device);
    if (result < 0) {
        ALOGE("Failed to create VkDevice err(%d)", result);
        return -1;
    }

    vkGetDeviceQueue(deviceInfo.device, deviceInfo.queueFamilyIndex, 0, &deviceInfo.queue);

    return 0;
}

static bool createVulkanSwapchain(VulkanInfo& vulkanInfo, bool setPreTransform) {
    const VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;
    VulkanSwapchainInfo& swapchainInfo = vulkanInfo.swapchainInfo;

    VkResult result;

    VkSurfaceCapabilitiesKHR surfaceCapabilities;
    result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(deviceInfo.gpu, deviceInfo.surface,
                                                       &surfaceCapabilities);
    if (result < 0) {
        ALOGE("Failed to get VkSurfaceCapabilitiesKHR err(%d)", result);
        return false;
    }
    ALOGD("Vulkan Surface Capabilities:\n");
    ALOGD("\timage count: %u - %u\n", surfaceCapabilities.minImageCount,
          surfaceCapabilities.maxImageCount);
    ALOGD("\tarray layers: %u\n", surfaceCapabilities.maxImageArrayLayers);
    ALOGD("\timage size (now): %dx%d\n", surfaceCapabilities.currentExtent.width,
          surfaceCapabilities.currentExtent.height);
    ALOGD("\timage size (extent): %dx%d - %dx%d\n", surfaceCapabilities.minImageExtent.width,
          surfaceCapabilities.minImageExtent.height, surfaceCapabilities.maxImageExtent.width,
          surfaceCapabilities.maxImageExtent.height);
    ALOGD("\tusage: %x\n", surfaceCapabilities.supportedUsageFlags);
    ALOGD("\tcurrent transform: %u\n", surfaceCapabilities.currentTransform);
    ALOGD("\tallowed transforms: %x\n", surfaceCapabilities.supportedTransforms);
    ALOGD("\tcomposite alpha flags: %u\n", surfaceCapabilities.supportedCompositeAlpha);

    uint32_t formatCount = 0;
    result = vkGetPhysicalDeviceSurfaceFormatsKHR(deviceInfo.gpu, deviceInfo.surface, &formatCount,
                                                  nullptr);
    if (result < 0) {
        ALOGE("Failed to get surface formats count err(%d)", result);
        return false;
    }

    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    result = vkGetPhysicalDeviceSurfaceFormatsKHR(deviceInfo.gpu, deviceInfo.surface, &formatCount,
                                                  formats.data());
    if (result < 0) {
        ALOGE("Failed to get surface formats err(%d)", result);
        return false;
    }

    uint32_t formatIndex;
    for (formatIndex = 0; formatIndex < formatCount; ++formatIndex) {
        if (formats[formatIndex].format == VK_FORMAT_R8G8B8A8_UNORM) {
            break;
        }
    }
    if (formatIndex == formatCount) {
        ALOGE("VK_FORMAT_R8G8B8A8_UNORM is not supported by any VkSurfaceFormatsKHR");
        return false;
    }

    swapchainInfo.displayFormat = formats[formatIndex].format;
    swapchainInfo.displaySize = surfaceCapabilities.currentExtent;

    VkSurfaceTransformFlagBitsKHR preTransform =
            (setPreTransform ? surfaceCapabilities.currentTransform
                             : VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR);
    ALOGD("currentTransform = %u, preTransform = %u",
          static_cast<uint32_t>(surfaceCapabilities.currentTransform),
          static_cast<uint32_t>(preTransform));

    const VkSwapchainCreateInfoKHR swapchainCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
            .pNext = nullptr,
            .flags = 0,
            .surface = deviceInfo.surface,
            .minImageCount = surfaceCapabilities.minImageCount,
            .imageFormat = swapchainInfo.displayFormat,
            .imageColorSpace = formats[formatIndex].colorSpace,
            .imageExtent = swapchainInfo.displaySize,
            .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
            .preTransform = preTransform,
            .imageArrayLayers = 1,
            .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
            .queueFamilyIndexCount = 1,
            .pQueueFamilyIndices = &deviceInfo.queueFamilyIndex,
            .compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
            .presentMode = VK_PRESENT_MODE_FIFO_KHR,
            .clipped = VK_FALSE,
    };
    result = vkCreateSwapchainKHR(deviceInfo.device, &swapchainCreateInfo, nullptr,
                                  &swapchainInfo.swapchain);
    if (result < 0) {
        ALOGE("Failed to create swapchain err(%d)", result);
        return false;
    }

    result = vkGetSwapchainImagesKHR(deviceInfo.device, swapchainInfo.swapchain,
                                     &swapchainInfo.imageCount, nullptr);
    if (result < 0) {
        ALOGE("Failed to get swapchain image count err(%d)", result);
        return false;
    }
    ALOGD("Swapchain length = %u", swapchainInfo.imageCount);

    std::vector<VkImage> images(swapchainInfo.imageCount);
    result = vkGetSwapchainImagesKHR(deviceInfo.device, swapchainInfo.swapchain,
                                     &swapchainInfo.imageCount, images.data());
    if (result < 0) {
        ALOGE("Failed to get swapchain images err(%d)", result);
        return false;
    }

    swapchainInfo.imageViews.resize(swapchainInfo.imageCount);
    for (uint32_t i = 0; i < swapchainInfo.imageCount; ++i) {
        const VkImageViewCreateInfo imageViewCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .image = images[i],
                .viewType = VK_IMAGE_VIEW_TYPE_2D,
                .format = swapchainInfo.displayFormat,
                .components =
                        {
                                .r = VK_COMPONENT_SWIZZLE_R,
                                .g = VK_COMPONENT_SWIZZLE_G,
                                .b = VK_COMPONENT_SWIZZLE_B,
                                .a = VK_COMPONENT_SWIZZLE_A,
                        },
                .subresourceRange =
                        {
                                .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                                .baseMipLevel = 0,
                                .levelCount = 1,
                                .baseArrayLayer = 0,
                                .layerCount = 1,
                        },
        };
        result = vkCreateImageView(deviceInfo.device, &imageViewCreateInfo, nullptr,
                                   &swapchainInfo.imageViews[i]);
        if (result < 0) {
            ALOGE("Failed to create image view(%d) err(%d)", i, result);
            return false;
        }
    }

    return true;
}

static bool createRenderPass(VkDevice device, VkFormat format, VkRenderPass& renderPass) {
    VkResult result;

    const VkAttachmentDescription attachmentDescription = {
            .flags = 0,
            .format = format,
            .samples = VK_SAMPLE_COUNT_1_BIT,
            .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
            .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
            .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
            .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
            .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
            .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
    };
    const VkAttachmentReference attachmentReference = {
            .attachment = 0,
            .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    };
    const VkSubpassDescription subpassDescription = {
            .flags = 0,
            .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
            .inputAttachmentCount = 0,
            .pInputAttachments = nullptr,
            .colorAttachmentCount = 1,
            .pColorAttachments = &attachmentReference,
            .pResolveAttachments = nullptr,
            .pDepthStencilAttachment = nullptr,
            .preserveAttachmentCount = 0,
            .pPreserveAttachments = nullptr,
    };
    const VkRenderPassCreateInfo renderPassCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .attachmentCount = 1,
            .pAttachments = &attachmentDescription,
            .subpassCount = 1,
            .pSubpasses = &subpassDescription,
            .dependencyCount = 0,
            .pDependencies = nullptr,
    };
    result = vkCreateRenderPass(device, &renderPassCreateInfo, nullptr, &renderPass);
    if (result < 0) {
        ALOGE("Failed to create render pass err(%d)", result);
        return false;
    }

    return true;
}

static bool mapMemoryTypeToIndex(VkPhysicalDevice gpu, uint32_t typeBits, VkFlags requirementsMask,
                                 uint32_t& typeIndex) {
    VkPhysicalDeviceMemoryProperties memoryProperties;
    vkGetPhysicalDeviceMemoryProperties(gpu, &memoryProperties);

    for (uint32_t i = 0; i < 32; ++i) {
        if ((typeBits & 1) == 1) {
            if ((memoryProperties.memoryTypes[i].propertyFlags & requirementsMask) ==
                requirementsMask) {
                typeIndex = i;
                return true;
            }
        }
        typeBits >>= 1;
    }
    return false;
}

static bool createBuffers(VkDevice device, VkPhysicalDevice gpu, uint32_t queueFamilyIndex,
                          VkDeviceMemory& memory, VkBuffer& buffer) {
    VkResult result;

    const VkBufferCreateInfo bufferCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .size = sizeof(vertexData),
            .usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
            .queueFamilyIndexCount = 1,
            .pQueueFamilyIndices = &queueFamilyIndex,
    };
    result = vkCreateBuffer(device, &bufferCreateInfo, nullptr, &buffer);
    if (result < 0) {
        ALOGE("Failed to create buffer err(%d)", result);
        return false;
    }

    VkMemoryRequirements memoryRequirements;
    vkGetBufferMemoryRequirements(device, buffer, &memoryRequirements);

    VkMemoryAllocateInfo memoryAllocateInfo = {
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .pNext = nullptr,
            .allocationSize = sizeof(vertexData),
            .memoryTypeIndex = 0,
    };

    memoryAllocateInfo.allocationSize = memoryRequirements.size;
    if (!mapMemoryTypeToIndex(gpu, memoryRequirements.memoryTypeBits,
                              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                              memoryAllocateInfo.memoryTypeIndex)) {
        return false;
    }

    result = vkAllocateMemory(device, &memoryAllocateInfo, nullptr, &memory);
    if (result < 0) {
        ALOGE("Failed to allocate memory for the buffer err(%d)", result);
        return false;
    }

    void* data;
    result = vkMapMemory(device, memory, 0, sizeof(vertexData), 0, &data);
    if (result < 0) {
        ALOGE("Failed to map memory err(%d)", result);
        return false;
    }
    memcpy(data, vertexData, sizeof(vertexData));
    vkUnmapMemory(device, memory);

    result = vkBindBufferMemory(device, buffer, memory, 0);
    if (result < 0) {
        ALOGE("Failed to bind buffer memory err(%d)", result);
        return false;
    }

    return true;
}

static bool loadShaderFromFile(VkDevice device, AAssetManager* assetManager, const char* filePath,
                               VkShaderModule& shader) {
    VkResult result;

    AAsset* file = AAssetManager_open(assetManager, filePath, AASSET_MODE_BUFFER);
    if (!file) {
        ALOGE("Failed to open shader file");
        return false;
    }
    size_t fileLength = AAsset_getLength(file);
    std::vector<char> fileContent(fileLength);
    AAsset_read(file, fileContent.data(), fileLength);
    AAsset_close(file);

    const VkShaderModuleCreateInfo shaderModuleCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .codeSize = fileLength,
            .pCode = (const uint32_t*)(fileContent.data()),
    };
    result = vkCreateShaderModule(device, &shaderModuleCreateInfo, nullptr, &shader);
    if (result < 0) {
        ALOGE("Failed to create shader module err(%d)", result);
        return false;
    }

    return true;
}

static bool createGraphicsPipeline(VulkanInfo& vulkanInfo, AAssetManager* assetManager) {
    const VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;
    const VulkanSwapchainInfo& swapchainInfo = vulkanInfo.swapchainInfo;
    const VulkanRenderInfo& renderInfo = vulkanInfo.renderInfo;
    VulkanPipelineInfo& pipelineInfo = vulkanInfo.pipelineInfo;

    VkResult result;

    const VkPushConstantRange pushConstantRange = {
            .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT,
            .offset = 0,
            .size = 3 * sizeof(float),
    };
    const VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .setLayoutCount = 0,
            .pSetLayouts = nullptr,
            .pushConstantRangeCount = 1,
            .pPushConstantRanges = &pushConstantRange,
    };
    result = vkCreatePipelineLayout(deviceInfo.device, &pipelineLayoutCreateInfo, nullptr,
                                    &pipelineInfo.layout);
    if (result < 0) {
        ALOGE("Failed to create pipeline layout err(%d)", result);
        return false;
    }

    VkShaderModule vertexShader, fragmentShader;
    if (!loadShaderFromFile(deviceInfo.device, assetManager, "shaders/tri.vert.spv",
                            vertexShader)) {
        return false;
    }
    if (!loadShaderFromFile(deviceInfo.device, assetManager, "shaders/tri.frag.spv",
                            fragmentShader)) {
        vkDestroyShaderModule(deviceInfo.device, vertexShader, nullptr);
        return false;
    }

    const VkPipelineShaderStageCreateInfo shaderStages[2] =
            {{
                     .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                     .pNext = nullptr,
                     .flags = 0,
                     .stage = VK_SHADER_STAGE_VERTEX_BIT,
                     .module = vertexShader,
                     .pName = "main",
                     .pSpecializationInfo = nullptr,
             },
             {
                     .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                     .pNext = nullptr,
                     .flags = 0,
                     .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
                     .module = fragmentShader,
                     .pName = "main",
                     .pSpecializationInfo = nullptr,
             }};
    const VkViewport viewports = {
            .x = 0.0f,
            .y = 0.0f,
            .width = (float)swapchainInfo.displaySize.width,
            .height = (float)swapchainInfo.displaySize.height,
            .minDepth = 0.0f,
            .maxDepth = 1.0f,
    };
    const VkRect2D scissor = {
            .offset =
                    {
                            .x = 0,
                            .y = 0,
                    },
            .extent = swapchainInfo.displaySize,
    };
    const VkPipelineViewportStateCreateInfo viewportInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .viewportCount = 1,
            .pViewports = &viewports,
            .scissorCount = 1,
            .pScissors = &scissor,
    };
    VkSampleMask sampleMask = ~0u;
    const VkPipelineMultisampleStateCreateInfo multisampleInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT,
            .sampleShadingEnable = VK_FALSE,
            .minSampleShading = 0,
            .pSampleMask = &sampleMask,
            .alphaToCoverageEnable = VK_FALSE,
            .alphaToOneEnable = VK_FALSE,
    };
    const VkPipelineColorBlendAttachmentState attachmentStates = {
            .blendEnable = VK_FALSE,
            .srcColorBlendFactor = (VkBlendFactor)0,
            .dstColorBlendFactor = (VkBlendFactor)0,
            .colorBlendOp = (VkBlendOp)0,
            .srcAlphaBlendFactor = (VkBlendFactor)0,
            .dstAlphaBlendFactor = (VkBlendFactor)0,
            .alphaBlendOp = (VkBlendOp)0,
            .colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                    VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT,
    };
    const VkPipelineColorBlendStateCreateInfo colorBlendInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .logicOpEnable = VK_FALSE,
            .logicOp = VK_LOGIC_OP_COPY,
            .attachmentCount = 1,
            .pAttachments = &attachmentStates,
            .blendConstants = {0.0f, 0.0f, 0.0f, 0.0f},
    };
    const VkPipelineRasterizationStateCreateInfo rasterInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .depthClampEnable = VK_FALSE,
            .rasterizerDiscardEnable = VK_FALSE,
            .polygonMode = VK_POLYGON_MODE_FILL,
            .cullMode = VK_CULL_MODE_NONE,
            .frontFace = VK_FRONT_FACE_CLOCKWISE,
            .depthBiasEnable = VK_FALSE,
            .depthBiasConstantFactor = 0,
            .depthBiasClamp = 0,
            .depthBiasSlopeFactor = 0,
            .lineWidth = 1,
    };
    const VkPipelineInputAssemblyStateCreateInfo inputAssemblyInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP,
            .primitiveRestartEnable = VK_FALSE,
    };
    const VkVertexInputBindingDescription vertexInputBindingDescription = {
            .binding = 0,
            .stride = 3 * sizeof(float),
            .inputRate = VK_VERTEX_INPUT_RATE_VERTEX,
    };
    const VkVertexInputAttributeDescription vertexInputAttributeDescription = {
            .location = 0,
            .binding = 0,
            .format = VK_FORMAT_R32G32B32_SFLOAT,
            .offset = 0,
    };
    const VkPipelineVertexInputStateCreateInfo vertexInputInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .vertexBindingDescriptionCount = 1,
            .pVertexBindingDescriptions = &vertexInputBindingDescription,
            .vertexAttributeDescriptionCount = 1,
            .pVertexAttributeDescriptions = &vertexInputAttributeDescription,
    };
    const VkPipelineCacheCreateInfo pipelineCacheInfo = {
            .sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .initialDataSize = 0,
            .pInitialData = nullptr,
    };
    result = vkCreatePipelineCache(deviceInfo.device, &pipelineCacheInfo, nullptr,
                                   &pipelineInfo.cache);
    if (result < 0) {
        ALOGE("Failed to create pipeline cache err(%d)", result);
        vkDestroyShaderModule(deviceInfo.device, vertexShader, nullptr);
        vkDestroyShaderModule(deviceInfo.device, fragmentShader, nullptr);
        return false;
    }

    const VkGraphicsPipelineCreateInfo pipelineCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
            .stageCount = 2,
            .pStages = shaderStages,
            .pVertexInputState = &vertexInputInfo,
            .pInputAssemblyState = &inputAssemblyInfo,
            .pTessellationState = nullptr,
            .pViewportState = &viewportInfo,
            .pRasterizationState = &rasterInfo,
            .pMultisampleState = &multisampleInfo,
            .pDepthStencilState = nullptr,
            .pColorBlendState = &colorBlendInfo,
            .pDynamicState = nullptr,
            .layout = pipelineInfo.layout,
            .renderPass = renderInfo.renderPass,
            .subpass = 0,
            .basePipelineHandle = VK_NULL_HANDLE,
            .basePipelineIndex = 0,
    };
    result = vkCreateGraphicsPipelines(deviceInfo.device, pipelineInfo.cache, 1,
                                       &pipelineCreateInfo, nullptr, &pipelineInfo.pipeline);
    if (result < 0) {
        ALOGE("Failed to create graphics pipelines err(%d)", result);
        vkDestroyShaderModule(deviceInfo.device, vertexShader, nullptr);
        vkDestroyShaderModule(deviceInfo.device, fragmentShader, nullptr);
        return false;
    }

    vkDestroyShaderModule(deviceInfo.device, vertexShader, nullptr);
    vkDestroyShaderModule(deviceInfo.device, fragmentShader, nullptr);

    return true;
}

static bool createVulkanRenderer(VulkanInfo& vulkanInfo, AAssetManager* assetManager) {
    const VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;
    const VulkanSwapchainInfo& swapchainInfo = vulkanInfo.swapchainInfo;
    const VulkanPipelineInfo& pipelineInfo = vulkanInfo.pipelineInfo;
    VulkanBufferInfo& bufferInfo = vulkanInfo.bufferInfo;
    VulkanRenderInfo& renderInfo = vulkanInfo.renderInfo;

    VkResult result;

    if (!createRenderPass(deviceInfo.device, swapchainInfo.displayFormat, renderInfo.renderPass)) {
        return false;
    }

    renderInfo.framebuffers.resize(swapchainInfo.imageCount);
    for (uint32_t i = 0; i < swapchainInfo.imageCount; ++i) {
        const VkFramebufferCreateInfo framebufferCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .renderPass = renderInfo.renderPass,
                .attachmentCount = 1,
                .pAttachments = &swapchainInfo.imageViews[i],
                .width = static_cast<uint32_t>(swapchainInfo.displaySize.width),
                .height = static_cast<uint32_t>(swapchainInfo.displaySize.height),
                .layers = 1,
        };
        result = vkCreateFramebuffer(deviceInfo.device, &framebufferCreateInfo, nullptr,
                                     &renderInfo.framebuffers[i]);
        if (result < 0) {
            ALOGE("Failed to create framebuffer(%u) err(%d)", i, result);
        }
    }

    if (!createBuffers(deviceInfo.device, deviceInfo.gpu, deviceInfo.queueFamilyIndex,
                       bufferInfo.memory, bufferInfo.vertexBuffer)) {
        return false;
    }

    if (!createGraphicsPipeline(vulkanInfo, assetManager)) {
        return false;
    }

    const VkCommandPoolCreateInfo commandPoolCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
            .pNext = nullptr,
            .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
            .queueFamilyIndex = deviceInfo.queueFamilyIndex,
    };
    result = vkCreateCommandPool(deviceInfo.device, &commandPoolCreateInfo, nullptr,
                                 &renderInfo.commandPool);
    if (result < 0) {
        ALOGE("Failed to create command pool err(%d)", result);
        return false;
    }

    renderInfo.commandBufferLength = swapchainInfo.imageCount;
    renderInfo.commandBuffers.resize(renderInfo.commandBufferLength);

    const VkCommandBufferAllocateInfo commandBufferAllocateInfo = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
            .pNext = nullptr,
            .commandPool = renderInfo.commandPool,
            .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            .commandBufferCount = renderInfo.commandBufferLength,
    };
    result = vkAllocateCommandBuffers(deviceInfo.device, &commandBufferAllocateInfo,
                                      renderInfo.commandBuffers.data());
    if (result < 0) {
        ALOGE("Failed to allocate command buffers err(%d)", result);
        return false;
    }

    for (uint32_t i = 0; i < renderInfo.commandBufferLength; ++i) {
        const VkCommandBufferBeginInfo commandBufferBeginInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                .pNext = nullptr,
                .flags = 0,
                .pInheritanceInfo = nullptr,
        };
        result = vkBeginCommandBuffer(renderInfo.commandBuffers[i], &commandBufferBeginInfo);
        if (result < 0) {
            ALOGE("Failed to begin command buffer(%d) err(%d)", i, result);
            return false;
        }

        const VkClearValue clearVals = {
                .color.float32[0] = 0.0f,
                .color.float32[1] = 0.0f,
                .color.float32[2] = 0.0f,
                .color.float32[3] = 1.0f,
        };
        const VkRenderPassBeginInfo renderPassBeginInfo = {
                .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
                .pNext = nullptr,
                .renderPass = renderInfo.renderPass,
                .framebuffer = renderInfo.framebuffers[i],
                .renderArea =
                        {
                                .offset =
                                        {
                                                .x = 0,
                                                .y = 0,
                                        },
                                .extent = swapchainInfo.displaySize,
                        },
                .clearValueCount = 1,
                .pClearValues = &clearVals,
        };
        vkCmdBeginRenderPass(renderInfo.commandBuffers[i], &renderPassBeginInfo,
                             VK_SUBPASS_CONTENTS_INLINE);

        vkCmdBindPipeline(renderInfo.commandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS,
                          pipelineInfo.pipeline);

        VkDeviceSize offset = 0;
        vkCmdBindVertexBuffers(renderInfo.commandBuffers[i], 0, 1, &bufferInfo.vertexBuffer,
                               &offset);

        vkCmdPushConstants(renderInfo.commandBuffers[i], pipelineInfo.layout,
                           VK_SHADER_STAGE_FRAGMENT_BIT, 0, 3 * sizeof(float), &fragData[0]);
        vkCmdDraw(renderInfo.commandBuffers[i], 4, 1, 0, 0);

        vkCmdPushConstants(renderInfo.commandBuffers[i], pipelineInfo.layout,
                           VK_SHADER_STAGE_FRAGMENT_BIT, 0, 3 * sizeof(float), &fragData[3]);
        vkCmdDraw(renderInfo.commandBuffers[i], 4, 1, 2, 0);

        vkCmdPushConstants(renderInfo.commandBuffers[i], pipelineInfo.layout,
                           VK_SHADER_STAGE_FRAGMENT_BIT, 0, 3 * sizeof(float), &fragData[6]);
        vkCmdDraw(renderInfo.commandBuffers[i], 4, 1, 6, 0);

        vkCmdPushConstants(renderInfo.commandBuffers[i], pipelineInfo.layout,
                           VK_SHADER_STAGE_FRAGMENT_BIT, 0, 3 * sizeof(float), &fragData[9]);
        vkCmdDraw(renderInfo.commandBuffers[i], 4, 1, 8, 0);

        vkCmdEndRenderPass(renderInfo.commandBuffers[i]);

        result = vkEndCommandBuffer(renderInfo.commandBuffers[i]);
        if (result < 0) {
            ALOGE("Failed to end command buffer(%d) err(%d)", i, result);
            return false;
        }
    }

    const VkFenceCreateInfo fenceCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
    };
    result = vkCreateFence(deviceInfo.device, &fenceCreateInfo, nullptr, &renderInfo.fence);
    if (result < 0) {
        ALOGE("Failed to create fence err(%d)", result);
        return false;
    }

    const VkSemaphoreCreateInfo semaphoreCreateInfo = {
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
            .pNext = nullptr,
            .flags = 0,
    };
    result = vkCreateSemaphore(deviceInfo.device, &semaphoreCreateInfo, nullptr,
                               &renderInfo.semaphore);
    if (result < 0) {
        ALOGE("Failed to create semaphore err(%d)", result);
        return false;
    }

    return true;
}

static bool drawFrame(VulkanInfo& vulkanInfo) {
    const VulkanDeviceInfo& deviceInfo = vulkanInfo.deviceInfo;
    const VulkanSwapchainInfo& swapchainInfo = vulkanInfo.swapchainInfo;
    const VulkanRenderInfo& renderInfo = vulkanInfo.renderInfo;

    VkResult result;

    uint32_t nextIndex;
    result = vkAcquireNextImageKHR(deviceInfo.device, swapchainInfo.swapchain, UINT64_MAX,
                                   renderInfo.semaphore, VK_NULL_HANDLE, &nextIndex);
    if (result < 0) {
        ALOGE("Failed to acquire next image err(%d)", result);
        return false;
    }

    result = vkResetFences(deviceInfo.device, 1, &renderInfo.fence);
    if (result < 0) {
        ALOGE("Failed to reset fences err(%d)", result);
        return false;
    }

    VkPipelineStageFlags waitStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    const VkSubmitInfo submitInfo = {
            .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
            .pNext = nullptr,
            .waitSemaphoreCount = 1,
            .pWaitSemaphores = &renderInfo.semaphore,
            .pWaitDstStageMask = &waitStageMask,
            .commandBufferCount = 1,
            .pCommandBuffers = &renderInfo.commandBuffers[nextIndex],
            .signalSemaphoreCount = 0,
            .pSignalSemaphores = nullptr,
    };
    result = vkQueueSubmit(deviceInfo.queue, 1, &submitInfo, renderInfo.fence);
    if (result < 0) {
        ALOGE("Failed to submit command buffer to a queue err(%d)", result);
        return false;
    }

    result = vkWaitForFences(deviceInfo.device, 1, &renderInfo.fence, VK_TRUE, 100000000);
    if (result != VK_SUCCESS) {
        ALOGE("Failed to wait for fences err(%d)", result);
        return false;
    }

    const VkPresentInfoKHR presentInfo = {
            .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
            .pNext = nullptr,
            .waitSemaphoreCount = 0,
            .pWaitSemaphores = nullptr,
            .swapchainCount = 1,
            .pSwapchains = &swapchainInfo.swapchain,
            .pImageIndices = &nextIndex,
            .pResults = &result,
    };
    result = vkQueuePresentKHR(deviceInfo.queue, &presentInfo);
    if (result < 0) {
        ALOGE("Failed to queue an image for presentation err(%d)", result);
        return false;
    }

    return true;
}

static ANativeWindow* window = nullptr;
static VulkanInfo vulkanInfo;

jint createNativeTest(JNIEnv* env, jclass /*clazz*/, jobject jAssetManager, jobject jSurface,
                      jboolean setPreTransform) {
    ALOGD("jboolean setPreTransform = %d", setPreTransform);
    if (!jAssetManager) {
        ALOGE("jAssetManager is NULL");
        return -1;
    }

    if (!jSurface) {
        ALOGE("jSurface is NULL");
        return -1;
    }

    AAssetManager* assetManager = AAssetManager_fromJava(env, jAssetManager);
    if (!assetManager) {
        ALOGE("Failed to get AAssetManager from jAssetManager");
        return -1;
    }

    window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        ALOGE("Failed to get ANativeWinodw from jSurface");
        return -1;
    }

    int ret = createVulkanDevice(vulkanInfo, window);
    if (ret < 0) {
        ALOGE("Failed to initialize Vulkan device");
        return -1;
    }
    if (ret > 0) {
        ALOGD("Hardware not supported");
        return 1;
    }

    if (!createVulkanSwapchain(vulkanInfo, setPreTransform)) {
        ALOGE("Failed to initialize Vulkan swapchain");
        return -1;
    }

    if (!createVulkanRenderer(vulkanInfo, assetManager)) {
        ALOGE("Failed to initialize Vulkan renderer");
        return -1;
    }

    for (uint32_t i = 0; i < 120; ++i) {
        if (!drawFrame(vulkanInfo)) {
            ALOGE("Failed to draw frame");
            return -1;
        }
    }

    return 0;
}

void destroyNativeTest(JNIEnv*, jclass) {
    releaseVulkan(vulkanInfo);
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

const std::array<JNINativeMethod, 2> JNI_METHODS = {{
        {"nCreateNativeTest", "(Landroid/content/res/AssetManager;Landroid/view/Surface;Z)I",
         (void*)createNativeTest},
        {"nDestroyNativeTest", "()V", (void*)destroyNativeTest},
}};

} // anonymous namespace

int register_android_graphics_cts_VulkanPreTransformCtsActivity(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/cts/VulkanPreTransformCtsActivity");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
