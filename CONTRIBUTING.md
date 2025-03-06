# Contributing

Bug fixes and new features are welcome! We use GitHub and request that changes are
submitted as pull requests. When uploading a PR, it will be reviewed by maintainers,
here are some pointers about what we're looking for:

- A PR should not contain unrelated changes
  - In particular do not include unrelated changes even despite how tempting it is to e.g. change
    `check(!something.isEmpty())` to `check(something.isNotEmpty())` in a unrelated file,
    clean up imports, or change whitespace elsewhere.
- We generally prefer a series of small commits to a single big commit but acknowledge
  there are cases where it's appropriate to have big commits / PRs.
- All new public API must have KDoc docstrings, no exceptions.
- Most of this code is library code which 3rd party depends on so care must be taken when
  exposing new APIs or removing or changing existing APIs. In the future we might have
  e.g. an `api.txt` file to more carefully control this.
- The commit message should be short, to the point, and explain exactly what the change is
  doing. Not too short, not too long. See the git history for inspiration and what the
  project is generally doing.
- The code in the change should follow the style in CODING-STYLE.md.
- The code must be tested, either with unit tests or manual tests. See TESTING.md for details.
- We generally use a single commit per PR, to keep the git history sane.

Before uploading a PR, it's generally a good idea to manually review the commit (using
e.g. `git show`) and check that it meets all the requirements above.
