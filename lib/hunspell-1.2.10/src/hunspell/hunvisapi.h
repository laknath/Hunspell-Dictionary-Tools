#ifndef _HUNSPELL_VISIBILITY_H_
#define _HUNSPELL_VISIBILITY_H_

#if defined(HUNSPELL_STATIC)
#define LIBHUNSPELL_DLL_EXPORTED
#elif BUILDING_LIBHUNSPELL && 1
#define LIBHUNSPELL_DLL_EXPORTED __attribute__((__visibility__("default")))
#elif BUILDING_LIBHUNSPELL && defined(_MSC_VER)
#define LIBHUNSPELL_DLL_EXPORTED __declspec(dllexport)
#elif defined _MSC_VER
#define LIBHUNSPELL_DLL_EXPORTED __declspec(dllimport)
#else
#define LIBHUNSPELL_DLL_EXPORTED
#endif

#endif
