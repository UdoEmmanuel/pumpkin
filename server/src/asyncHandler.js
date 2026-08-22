// Express 4 does NOT automatically catch a rejected Promise thrown from an
// async route handler — an unhandled rejection there just leaves the
// request hanging forever with no response ever sent, no error logged
// (unless you happen to have unhandledRejection logging, which this
// project didn't). This wrapper forwards any rejection to next(err) so the
// global error handler in index.js can actually respond.
function asyncHandler(fn) {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = { asyncHandler };
