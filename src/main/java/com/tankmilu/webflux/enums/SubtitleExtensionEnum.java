package com.tankmilu.webflux.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum SubtitleExtensionEnum {
    SMI(List.of("smi","sami")),
    SRT(List.of("srt")),
    ASS(List.of("ass", "ssa")),
    IDX(List.of("idx","sub")),
    ;


    private final List<String> extensions;

    SubtitleExtensionEnum(List<String> extensions) {
        this.extensions = extensions;
    }

    private static String extractExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    public boolean isSupportedFile(String fileName) {
        String extension = extractExtension(fileName);
        if (extension == null) {
            return false;
        }
        return extensions.stream()
                .anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }

    // 입력된 파일이 등록된 자막 파일인지 검사하는 메소드
    public static boolean isSubtitle(String fileName) {
        if (fileName == null) {
            return false;
        }
        if (extractExtension(fileName) == null) {
            return false;
        }
        for (SubtitleExtensionEnum subtitle : SubtitleExtensionEnum.values()) {
            if (subtitle.isSupportedFile(fileName)) {
                return true;
            }
        }
        return false;
    }




    public static List<String> generateSubtitleNames(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return List.of();
        }

        // 원본 파일명에서 확장자 제거
        String baseName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            baseName = fileName; // 확장자가 없는 경우 전체를 베이스로 처리
        } else {
            baseName = fileName.substring(0, lastDotIndex);
        }

        //  모든 자막 확장자 수집
        List<String> allSubExtensions = Arrays.stream(SubtitleExtensionEnum.values())
                .flatMap(sub -> sub.getExtensions().stream())
                .toList();

        // 베이스 이름 + 모든 자막 확장자 조합 생성
        return allSubExtensions.stream()
                .map(ext -> baseName + "." + ext)
                .collect(Collectors.toList());
    }

}
