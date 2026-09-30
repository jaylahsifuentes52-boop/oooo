package com.juniormod.juniorpatcher;

interface IPatchService {
    void destroy() = 16777114;
    String copyObb() = 1;
    String restoreCache() = 2;
    String checkAuthMarker(String marker) = 3;
}
