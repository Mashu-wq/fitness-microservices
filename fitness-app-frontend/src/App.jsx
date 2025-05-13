import { Button } from "@mui/material";
import {
  BrowserRouter as Router,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router";

function App() {
  return (
    <Router>
      <Button variant="contained" color="primary">
        {" "}
        LOGIN{" "}
      </Button>
    </Router>
  );
}

export default App;
