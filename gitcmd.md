# Git Command Reference

Common commands for day-to-day branch/push/pull workflows.

## Setup / status

```bash
git status                     # what's staged, unstaged, untracked
git branch -a                  # list local + remote branches
git remote -v                  # show remote name(s) and URL(s)
git log --oneline -10          # last 10 commits, one line each
git diff                       # unstaged changes
git diff --staged              # staged changes
```

## Pulling

```bash
git fetch origin                       # download remote refs, don't merge
git pull                               # fetch + merge current branch's upstream
git pull origin <branch>               # fetch + merge a specific remote branch
git pull --rebase origin <branch>      # same, but rebase instead of merge
```

## Pushing

```bash
git push                               # push current branch to its upstream
git push origin <branch>               # push current branch to a named remote branch
git push -u origin <branch>            # push and set upstream tracking (first push)
git push origin <local>:<remote>       # push local branch to a differently-named remote branch
```

## Creating a new branch

```bash
git branch <branch-name>               # create branch, stay on current one
git checkout -b <branch-name>          # create branch and switch to it
git switch -c <branch-name>            # same as above (newer syntax)

# branch off a specific starting point
git checkout -b <branch-name> origin/main
git checkout -b <branch-name> <commit-sha>
```

## Pushing to a new branch (first time)

```bash
git checkout -b feature/my-change
# ... make changes, commit ...
git add <files>
git commit -m "message"
git push -u origin feature/my-change   # -u links local branch to remote so future
                                         # `git push`/`git pull` need no arguments
```

## Pulling from a branch that doesn't exist locally yet

```bash
git fetch origin
git checkout -b <branch-name> origin/<branch-name>   # creates local branch tracking remote
# or, once it exists locally and is tracking:
git pull
```

## Switching branches

```bash
git checkout <branch-name>             # switch to existing local branch
git switch <branch-name>               # same (newer syntax)
git checkout -                         # switch back to the previous branch
```

## Merging / rebasing a branch

```bash
git checkout main
git merge <branch-name>                # merge branch-name into current branch
git rebase main                        # replay current branch's commits on top of main
```

## Deleting branches

```bash
git branch -d <branch-name>            # delete local branch (safe, blocks if unmerged)
git branch -D <branch-name>            # force-delete local branch (unmerged commits lost)
git push origin --delete <branch-name> # delete the branch on the remote
```

## Working with multiple checkouts at once (worktrees)

Useful when you need two branches checked out simultaneously without stashing.

```bash
git worktree add ../wt-branch origin/<branch-name>   # new working dir on that branch
git worktree list                                     # show active worktrees
git worktree remove ../wt-branch                      # remove when done
```

## Undoing things (non-destructive first)

```bash
git restore <file>                     # discard unstaged changes to a file
git restore --staged <file>            # unstage a file (keep the edits)
git stash                              # shelve uncommitted changes
git stash pop                          # reapply the most recent stash
git revert <commit>                    # create a new commit that undoes <commit>
```

## Undoing things (destructive — use with care)

```bash
git reset --hard <commit>              # move branch pointer, DISCARD local changes
git clean -fd                          # DELETE untracked files/directories
git push --force origin <branch>       # overwrite remote history (never on shared/main branches)
```

## Quick reference: this repo's remotes/branches

```bash
git remote -v
# origin  https://github.com/kaju112/folder.git

git branch -a
# * maven-restructure
#   remotes/origin/HEAD -> origin/main
#   remotes/origin/kdm_cobol
#   remotes/origin/main
#   remotes/origin/maven-restructure
```

Example: checking out `kdm_cobol` locally and pushing a new commit to it:

```bash
git fetch origin
git checkout -b kdm_cobol origin/kdm_cobol   # first time only
git checkout kdm_cobol                        # subsequent times

# ... make changes ...
git add <files>
git commit -m "message"
git push origin kdm_cobol
```
